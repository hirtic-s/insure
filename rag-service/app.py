"""
app.py — RAG briefing service.

POST /briefing takes a scored claim, retrieves the most relevant investigator
guidance from pgvector, and asks Gemini for a briefing that cites its sources.
"""

import re
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

import rag

state: dict = {}


@asynccontextmanager
async def lifespan(application: FastAPI):
    conn = rag.connect()
    rag.init_schema(conn)
    state["conn"] = conn
    yield
    conn.close()


app = FastAPI(title="InSure! RAG Briefing Service", lifespan=lifespan)


class Claim(BaseModel):
    """Mirrors the backend's ClaimView JSON (camelCase)."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="ignore")

    id: str
    policy_number: str
    claimant_name: str
    incident_description: str | None = None
    claim_amount: float
    incident_date: str
    submitted_at: str
    status: str
    driver_age: int
    gender: str
    marital_status: str
    accident_site: str
    witness_present: bool
    police_report_filed: bool
    injury_claim: float | None = None
    risk_score: float | None = None
    top_factors: str | None = None


def _claim_facts(c: Claim) -> str:
    return "\n".join(
        [
            f"Claim ID: {c.id}",
            f"Status: {c.status}",
            f"Fraud risk score: {c.risk_score if c.risk_score is not None else 'not scored'}",
            f"Model factors (SHAP): {c.top_factors or 'none'}",
            f"Claim amount: {c.claim_amount}; injury component: {c.injury_claim or 0}",
            f"Incident date: {c.incident_date}; submitted: {c.submitted_at}",
            f"Accident site: {c.accident_site}",
            f"Witness present: {c.witness_present}; police report filed: {c.police_report_filed}",
            f"Driver: age {c.driver_age}, {c.gender}, marital status {c.marital_status}",
            f"Description: {c.incident_description or 'none provided'}",
        ]
    )


PROMPT = """You are assisting an insurance fraud investigator. Draft a concise briefing \
(under 250 words) for the claim below.

Rules:
- Use ONLY the claim record and the numbered guidance sources. Do not invent facts.
- Cite guidance with its tag, e.g. [S1], after each statement it supports. Cite the claim record as [C].
- Structure: Summary; Risk signals; Recommended next steps.
- The risk score prioritises review; it is not proof of fraud. Never recommend denial on \
demographic factors.

CLAIM RECORD [C]:
{facts}

GUIDANCE SOURCES:
{sources}
"""


@app.post("/briefing")
def briefing(claim: Claim):
    facts = _claim_facts(claim)
    query = (
        f"{claim.top_factors or ''} accident at {claim.accident_site}; "
        f"witness {claim.witness_present}; police report {claim.police_report_filed}; "
        f"claim {claim.claim_amount} injury {claim.injury_claim or 0}"
    )
    try:
        hits = rag.retrieve(state["conn"], query, k=4)
        sources = "\n\n".join(f"[S{i}] {h['title']}\n{h['content']}" for i, h in enumerate(hits, 1))
        resp = rag.client().models.generate_content(
            model=rag.GEN_MODEL, contents=PROMPT.format(facts=facts, sources=sources)
        )
    except Exception as e:  # noqa: BLE001 — surface any upstream failure as 502
        raise HTTPException(status_code=502, detail=f"Briefing generation failed: {e}") from e

    text = resp.text or ""
    cited = {int(n) for n in re.findall(r"\[S(\d+)\]", text)}
    citations = [
        {"tag": f"S{i}", **h} for i, h in enumerate(hits, 1) if i in cited
    ]
    return {"briefing": text, "citations": citations, "retrieved": len(hits)}


@app.post("/ingest")
def ingest():
    return {"chunks_ingested": rag.ingest(state["conn"])}


@app.get("/health")
def health():
    return {"status": "ok"}
