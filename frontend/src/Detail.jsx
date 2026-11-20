import Briefing from "./Briefing.jsx";
import { ArrowDownIcon, ArrowUpIcon, BackIcon } from "./icons.jsx";
import { BAND_LABEL, band, duration, factorParts, pct, redFlags } from "./format.js";

export default function Detail({ claim, onClose }) {
  const b = band(claim);
  const f = claim.features;
  const factors = claim.topFactors ? claim.topFactors.split("; ").map(factorParts) : [];
  const maxWeight = Math.max(0, ...factors.map((x) => x.weight ?? 0));
  const flags = redFlags(f);

  return (
    <aside className="detail" aria-label="Claim detail">
      <button type="button" className="btn ghost back" onClick={onClose}><BackIcon /> Back to queue</button>

      <header className="d-head">
        <div>
          <h2>{claim.claimantName}</h2>
          <p className="muted">{claim.policyNumber}</p>
        </div>
        <div className={`d-score ${b}`}>
          <span className="num">{claim.riskScore == null ? "—" : `${pct(claim.riskScore)}%`}</span>
          <span>{claim.riskScore == null ? "Not scored" : `${BAND_LABEL[b]} risk · typical is 6%`}</span>
        </div>
      </header>

      {claim.riskScore != null && (
        <div className={`gauge ${b}`} aria-hidden="true">
          <i style={{ width: `${Math.min(100, claim.riskScore * 250)}%` }} />
          <b style={{ left: "15%" }} title="Typical claim: 6%" />
        </div>
      )}

      {claim.riskScore == null && (
        <p className="notice">This claim hasn't been scored yet. That is not the same as low risk; it will be retried automatically, or review it manually.</p>
      )}

      {factors.length > 0 && (
        <section className="block">
          <h3>Why it scored this way</h3>
          <ul className="factors">
            {factors.map((x) => (
              <li key={x.label} className={x.up ? "up" : "down"}>
                {x.up ? <ArrowUpIcon /> : <ArrowDownIcon />}
                <span className="f-label">{x.label}</span>
                {x.weight != null && maxWeight > 0 ? (
                  <span className="f-bar" title={x.up ? "Raises risk" : "Lowers risk"}>
                    <i style={{ width: `${Math.max(6, (x.weight / maxWeight) * 100)}%` }} />
                  </span>
                ) : (
                  <span className="dir">{x.up ? "raises risk" : "lowers risk"}</span>
                )}
              </li>
            ))}
          </ul>
          <p className="f-note">Bars compare the size of each factor. The longest one carries most of the score, so a few small factors pointing the other way may not move it.</p>
        </section>
      )}

      {flags.length > 0 && (
        <section className="block">
          <h3>Worth checking by hand</h3>
          <ul className="flags">
            {flags.map((t) => <li key={t}>{t}</li>)}
          </ul>
          <p className="f-note">Read from the claim itself, whatever the score. Several are common in ordinary claims, so they are prompts to look, not signs of fraud.</p>
        </section>
      )}

      <Briefing key={claim.id} claimId={claim.id} />

      <section className="block">
        <h3>Claim</h3>
        <dl>
          <dt>At fault</dt><dd>{f.Fault}</dd>
          <dt>Policy</dt><dd>{f.PolicyType}</dd>
          <dt>Accident area</dt><dd>{f.AccidentArea}</dd>
          <dt>Vehicle</dt><dd>{f.Make}, {f.VehicleCategory}</dd>
          <dt>Vehicle price</dt><dd>{f.VehiclePrice}</dd>
          <dt>Driver</dt><dd className="num">{f.Age}, {f.Sex}</dd>
          <dt>Past claims</dt><dd>{f.PastNumberOfClaims}</dd>
          <dt>Address change</dt><dd>{f.AddressChange_Claim}</dd>
          <dt>Witness</dt><dd>{f.WitnessPresent}</dd>
          <dt>Police report</dt><dd>{f.PoliceReportFiled}</dd>
          <dt>Time to score</dt><dd className="num">{duration(claim.scoringLatencyMs)}</dd>
        </dl>
      </section>
    </aside>
  );
}
