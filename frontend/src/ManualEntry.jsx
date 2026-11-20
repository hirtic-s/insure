import { useState } from "react";
import schema from "./claimSchema.json";

const MIN = 1;
const MAX = 25;

const defaults = () =>
  Object.fromEntries(Object.entries(schema.fields).map(([k, f]) => [k, String(f.default)]));

const blank = () => ({ name: "", values: defaults() });

function Field({ id, spec, value, onChange }) {
  return (
    <label className="field" htmlFor={id}>
      <span>{spec.label}</span>
      {spec.type === "number" ? (
        <input id={id} type="number" inputMode="numeric" min={spec.min} max={spec.max} value={value}
          onChange={(e) => onChange(e.target.value)} />
      ) : (
        <select id={id} value={value} onChange={(e) => onChange(e.target.value)}>
          {spec.options.map((o) => <option key={o} value={o}>{o}</option>)}
        </select>
      )}
    </label>
  );
}

export default function ManualEntry({ onSubmit, error, busy }) {
  const [count, setCount] = useState(1);
  const [claims, setClaims] = useState([blank()]);
  const [problem, setProblem] = useState(null);

  const resize = (n) => {
    const next = Math.max(MIN, Math.min(MAX, Number.isFinite(n) ? n : MIN));
    setCount(next);
    setClaims((cur) => (next <= cur.length ? cur.slice(0, next) : [...cur, ...Array.from({ length: next - cur.length }, blank)]));
  };

  const update = (i, patch) => setClaims((cur) => cur.map((c, j) => (j === i ? { ...c, ...patch } : c)));
  const setValue = (i, key, v) => update(i, { values: { ...claims[i].values, [key]: v } });

  const submit = (e) => {
    e.preventDefault();
    const bad = claims.findIndex((c) => !(Number(c.values.Age) >= 16 && Number(c.values.Age) <= 100));
    if (bad >= 0) return setProblem(`Claim ${bad + 1}: driver age must be between 16 and 100.`);
    setProblem(null);
    onSubmit(claims.map((c) => ({ claimantName: c.name, features: c.values })));
  };

  return (
    <form className="manual" onSubmit={submit}>
      <div className="m-head">
        <div>
          <p className="dz-title">Enter claims by hand</p>
          <p className="dz-hint">Fields start at the most common value in the training data, so change every one that applies to the claim.</p>
        </div>
        <div className="stepper" role="group" aria-label="Number of claims">
          <label htmlFor="claim-count">Claims</label>
          <button type="button" className="btn" aria-label="Fewer claims" disabled={count <= MIN} onClick={() => resize(count - 1)}>−</button>
          <input id="claim-count" type="number" min={MIN} max={MAX} value={count} onChange={(e) => resize(parseInt(e.target.value, 10))} />
          <button type="button" className="btn" aria-label="More claims" disabled={count >= MAX} onClick={() => resize(count + 1)}>+</button>
        </div>
      </div>

      <div className="m-list">
        {claims.map((c, i) => (
          <details key={i} className="m-claim" open={i === 0}>
            <summary>
              <strong>Claim {i + 1}</strong>
              <span className="muted">{c.name || "no label"} · {c.values.PolicyType}, {c.values.Fault === "Policy Holder" ? "policyholder at fault" : "third party at fault"}</span>
            </summary>
            <div className="m-body">
              <div className="m-top">
                <label className="field" htmlFor={`name-${i}`}>
                  <span>Label (optional)</span>
                  <input id={`name-${i}`} type="text" maxLength={60} placeholder={`Manual claim ${i + 1}`} value={c.name}
                    onChange={(e) => update(i, { name: e.target.value })} />
                </label>
                {i > 0 && (
                  <button type="button" className="btn ghost" onClick={() => update(i, { values: { ...claims[i - 1].values } })}>
                    Copy from claim {i}
                  </button>
                )}
              </div>
              {schema.groups.map((g) => (
                <fieldset key={g.title}>
                  <legend>{g.title}</legend>
                  <div className="m-grid">
                    {g.fields.map((key) => (
                      <Field key={key} id={`${key}-${i}`} spec={schema.fields[key]} value={c.values[key]} onChange={(v) => setValue(i, key, v)} />
                    ))}
                  </div>
                </fieldset>
              ))}
            </div>
          </details>
        ))}
      </div>

      {(problem || error) && <p className="dz-error" role="alert">{problem || error}</p>}
      <div className="m-foot">
        <button type="submit" className="btn primary" disabled={busy}>{busy ? "Sending…" : `Score ${count} ${count === 1 ? "claim" : "claims"}`}</button>
      </div>
    </form>
  );
}
