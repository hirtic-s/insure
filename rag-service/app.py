"""
app.py: RAG briefing service.

POST /briefing takes a scored claim, retrieves the most relevant investigator guidance
from pgvector, and asks Gemini for a briefing that cites its sources. Briefings are cached
per claim, so reopening a claim costs no API call.
"""

import hashlib
import json
import logging
import re
from contextlib import asynccontextmanager
from typing import Any

from fastapi import FastAPI, HTTPException
from google.genai import errors as genai_errors
from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

import rag

log = logging.getLogger("rag")


@asynccontextmanager
async def lifespan(application: FastAPI):
    with rag.connect() as conn:
        rag.init_schema(conn)
        try:
            log.info("knowledge base ready: %s chunks", rag.ingest(conn))
        except rag.NotConfigured as e:
            log.warning("%s Set it, then POST /ingest.", e)
        except Exception:  # noqa: BLE001 - the service stays up; /ingest can be retried
            log.exception("knowledge ingest failed at startup")
    yield


app = FastAPI(title="InSure! RAG Briefing Service", lifespan=lifespan)


class Claim(BaseModel):
    """Mirrors the backend's ClaimView JSON (camelCase)."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="ignore")

    id: str
    policy_number: str
    claimant_name: str
    incident_description: str | None = None
    status: str
    features: dict[str, Any]
    risk_score: float | None = None
    risk_band: str | None = None
    top_factors: str | None = None


def hand_check_flags(f: dict[str, Any]) -> list[str]:
    """Facts a reviewer would check by hand, read straight from the claim. Mirrors the dashboard."""
    flags = []
    if f.get("Fault") == "Policy Holder":
        flags.append("Policyholder is at fault")
    if f.get("PoliceReportFiled") == "No":
        flags.append("No police report filed")
    if f.get("WitnessPresent") == "No":
        flags.append("No witness")
    if f.get("PastNumberOfClaims") in ("2 to 4", "more than 4"):
        flags.append(f"Past claims: {f['PastNumberOfClaims']}")
    if f.get("AddressChange_Claim") in ("under 6 months", "1 year"):
        flags.append(f"Address changed ({f['AddressChange_Claim']}) before the claim")
    if f.get("Days_Policy_Accident") == "1 to 7":
        flags.append("Accident within a week of the policy starting")
    return flags


def claim_facts(c: Claim, flags: list[str]) -> str:
    score = f"{c.risk_score * 100:.1f}% ({c.risk_band})" if c.risk_score is not None else "not scored"
    lines = [
        f"Claim: {c.claimant_name} / policy {c.policy_number}",
        f"Status: {c.status}",
        f"Fraud risk score: {score}; typical claim is about 6%",
        f"Model factors (SHAP, log-odds in brackets): {c.top_factors or 'none'}",
        f"Hand-check points: {'; '.join(flags) if flags else 'none'}",
        "Fields:",
        *(f"  {k}: {v}" for k, v in c.features.items()),
    ]
    if c.incident_description:
        lines.append(f"Description: {c.incident_description}")
    return "\n".join(lines)


PROMPT = """You are assisting an insurance fraud investigator. Draft a concise briefing \
(under 300 words) for the claim below.

Rules:
- Use ONLY the claim record and the numbered guidance sources. Do not invent facts, \
statistics or policy rules.
- Cite guidance with its tag, e.g. [S1], right after each statement it supports. Cite facts from \
the claim record as [C]. One tag per bracket: write [C][S1], never [C, S1]. Never cite a tag that is not listed.
- The score ranks review; it is not proof of fraud. Never suggest denying or reducing a claim, \
and never treat age, sex, marital status or month as evidence.
- If a Low score rests mostly on one policy factor, say so plainly.
- Format exactly: four sections headed "## Summary", "## Why it scored this way", \
"## Check by hand", "## Suggested next steps". Use "- " bullets under the last three. No other markup.

CLAIM RECORD [C]:
{facts}

GUIDANCE SOURCES:
{sources}
"""


def split_tags(text: str) -> str:
    """Models sometimes merge tags as [C, S1]; write them as [C][S1] so each one can be parsed."""
    return re.sub(
        r"\[((?:S\d+|C)(?:\s*,\s*(?:S\d+|C))+)\]",
        lambda m: "".join(f"[{t.strip()}]" for t in m.group(1).split(",")),
        text,
    )


def _input_hash(facts: str) -> str:
    return hashlib.sha256(f"{facts}|{rag.knowledge_hash()}|{rag.GEN_MODEL}".encode()).hexdigest()[:24]


def _upstream_error(e: Exception) -> HTTPException:
    if isinstance(e, rag.NotConfigured):
        return HTTPException(status_code=503, detail=str(e))
    if isinstance(e, genai_errors.APIError) and e.code == 429:
        return HTTPException(status_code=429, detail="Gemini rate limit reached. Try again in a minute.")
    log.exception("briefing generation failed")
    return HTTPException(status_code=502, detail="The briefing could not be generated. Try again shortly.")


@app.post("/briefing")
def briefing(claim: Claim, refresh: bool = False):
    flags = hand_check_flags(claim.features)
    facts = claim_facts(claim, flags)
    key = _input_hash(facts)

    with rag.connect() as conn:
        if not refresh and (hit := rag.cached_briefing(conn, claim.id, key)):
            return {**hit, "cached": True, "retrieved": len(hit["citations"])}
        try:
            rag.ingest(conn)  # no-op unless the knowledge files changed
            query = " ".join([re.sub(r"\[[^\]]*\]", "", claim.top_factors or ""), *flags, "interpreting the risk score"])
            hits = rag.retrieve(conn, query, k=5)
            sources = "\n\n".join(f"[S{i}] {h['title']}\n{h['content']}" for i, h in enumerate(hits, 1))
            resp = rag.client().models.generate_content(
                model=rag.GEN_MODEL,
                contents=PROMPT.format(facts=facts, sources=sources),
                config={"temperature": 0.2},
            )
        except Exception as e:  # noqa: BLE001 - mapped to a clean HTTP error
            raise _upstream_error(e) from e

        text = split_tags((resp.text or "").strip())
        if not text:
            raise HTTPException(status_code=502, detail="Gemini returned an empty briefing. Try again.")
        cited = {int(n) for n in re.findall(r"\[S(\d+)\]", text)}
        citations = [{"tag": f"S{i}", **h} for i, h in enumerate(hits, 1) if i in cited]
        rag.store_briefing(conn, claim.id, key, text, citations)
    return {"briefing": text, "citations": citations, "retrieved": len(hits), "model": rag.GEN_MODEL, "cached": False}


@app.post("/ingest")
def ingest(force: bool = True):
    try:
        with rag.connect() as conn:
            return {"chunks": rag.ingest(conn, force=force)}
    except Exception as e:  # noqa: BLE001
        raise _upstream_error(e) from e


@app.get("/health")
def health():
    return {"status": "ok"}
