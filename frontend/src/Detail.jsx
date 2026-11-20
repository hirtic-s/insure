import { useEffect, useState } from "react";
import { requestBriefing } from "./api.js";
import { ArrowDownIcon, ArrowUpIcon, BackIcon } from "./icons.jsx";
import { BAND_LABEL, band, duration, factorParts, money, pct } from "./format.js";

// Turns [S1]/[C] tags in model output into superscript citations.
function Cited({ text }) {
  return text.split(/(\[(?:S\d+|C)\])/g).map((part, i) =>
    /^\[(S\d+|C)\]$/.test(part)
      ? <sup key={i} className="cite">{part.slice(1, -1)}</sup>
      : <span key={i}>{part}</span>
  );
}

function Briefing({ claimId }) {
  const [state, setState] = useState({ status: "idle" });
  useEffect(() => setState({ status: "idle" }), [claimId]);

  const draft = async () => {
    setState({ status: "loading" });
    try {
      setState({ status: "done", data: await requestBriefing(claimId) });
    } catch (e) {
      setState({ status: "error", message: e.message === "Failed to fetch" ? "service unreachable" : e.message });
    }
  };

  return (
    <section className="block">
      <h3>Briefing</h3>
      {state.status === "done" ? (
        <>
          <p className="briefing"><Cited text={state.data.briefing} /></p>
          <ol className="sources">
            {state.data.citations.map((c) => (
              <li key={c.tag}><span className="tag">{c.tag}</span> {c.title} <span className="muted num">{pct(c.similarity)}% match</span></li>
            ))}
          </ol>
        </>
      ) : (
        <>
          <p className="muted">Drafts a short summary with next steps, citing the guidance it used.</p>
          <button type="button" className="btn" onClick={draft} disabled={state.status === "loading"}>
            {state.status === "loading" ? "Drafting…" : "Draft briefing"}
          </button>
          {state.status === "error" && (
            <p className="inline-error" role="alert">Couldn't draft a briefing ({state.message}). The briefing service needs to be running with a Gemini key; the score and factors above are unaffected.</p>
          )}
        </>
      )}
    </section>
  );
}

export default function Detail({ claim, onClose }) {
  const b = band(claim.riskScore);
  const factors = claim.topFactors ? claim.topFactors.split("; ").map(factorParts) : [];

  return (
    <aside className="detail" aria-label="Claim detail">
      <button type="button" className="btn ghost back" onClick={onClose}><BackIcon /> Back to queue</button>

      <header className="d-head">
        <div>
          <h2>{claim.claimantName}</h2>
          <p className="muted">{claim.policyNumber}</p>
        </div>
        <div className={`d-score ${b}`}>
          <span className="num">{claim.riskScore == null ? "—" : pct(claim.riskScore)}</span>
          <span>{claim.riskScore == null ? "Not scored" : `${BAND_LABEL[b]} risk`}</span>
        </div>
      </header>

      {claim.riskScore == null && (
        <p className="notice">This claim hasn't been scored yet. That is not the same as low risk; it will be retried automatically, or review it manually.</p>
      )}

      {factors.length > 0 && (
        <section className="block">
          <h3>Why it scored this way</h3>
          <ul className="factors">
            {factors.map((f) => (
              <li key={f.label} className={f.up ? "up" : "down"}>
                {f.up ? <ArrowUpIcon /> : <ArrowDownIcon />}
                <span>{f.label}</span>
                <span className="dir">{f.up ? "raises risk" : "lowers risk"}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="block">
        <h3>Claim</h3>
        <dl>
          <dt>Amount</dt><dd className="num">{money(claim.claimAmount)}</dd>
          <dt>Injury portion</dt><dd className="num">{money(claim.injuryClaim)}</dd>
          <dt>Accident site</dt><dd>{claim.accidentSite}</dd>
          <dt>Driver</dt><dd className="num">{claim.driverAge}, {claim.gender}</dd>
          <dt>Witness</dt><dd>{claim.witnessPresent ? "Yes" : "No"}</dd>
          <dt>Police report</dt><dd>{claim.policeReportFiled ? "Yes" : "No"}</dd>
          <dt>Time to score</dt><dd className="num">{duration(claim.scoringLatencyMs)}</dd>
        </dl>
      </section>

      <Briefing claimId={claim.id} />
    </aside>
  );
}
