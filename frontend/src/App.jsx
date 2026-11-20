import { useCallback, useEffect, useMemo, useState } from "react";
import { getClaims, getSummary } from "./api.js";
import Upload from "./Upload.jsx";
import Detail from "./Detail.jsx";
import { SearchIcon } from "./icons.jsx";
import { BAND_LABEL, band, pct } from "./format.js";

const BASE_FILTERS = ["all", "high", "medium", "low", "unscored"];

function RiskCell({ claim }) {
  const score = claim.riskScore;
  const b = band(claim);
  return (
    <span className={`risk ${b}`}>
      <span className="num">{score == null ? "—" : `${pct(score)}%`}</span>
      <span className="meter" aria-hidden="true"><i style={{ width: `${score == null ? 0 : Math.min(100, score * 250)}%` }} /></span>
    </span>
  );
}

export default function App() {
  const [claims, setClaims] = useState(null);
  const [batchClaims, setBatchClaims] = useState([]);
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [filter, setFilter] = useState("all");
  const [query, setQuery] = useState("");
  const [batchId, setBatchId] = useState(null);

  const refresh = useCallback(async () => {
    try {
      // The batch is fetched on its own: a low-risk claim from it may fall outside the top-200 queue.
      const [c, s, b] = await Promise.all([getClaims(), getSummary(), batchId ? getClaims(batchId) : []]);
      setClaims(c);
      setBatchClaims(b);
      setSummary(s);
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, [batchId]);

  useEffect(() => {
    refresh();
    const t = setInterval(refresh, 6000);
    return () => clearInterval(t);
  }, [refresh]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (filter === "batch" ? batchClaims : claims ?? []).filter(
      (c) =>
        (filter === "all" || filter === "batch" || band(c) === filter) &&
        (!q || c.claimantName.toLowerCase().includes(q) || c.policyNumber.toLowerCase().includes(q))
    );
  }, [claims, batchClaims, filter, query]);

  const selected = [...(claims ?? []), ...batchClaims].find((c) => c.id === selectedId);
  const filters = batchId ? ["batch", ...BASE_FILTERS] : BASE_FILTERS;
  const count = (f) => f === "batch" ? batchClaims.length : f === "all" ? summary?.total : summary?.[f];

  return (
    <div className={`app${selected ? " has-selection" : ""}`}>
      <header className="topbar">
        <span className="wordmark">InSure<span>!</span></span>
        <span className="crumb">Claims review</span>
        <span className="live" title="Queue refreshes every 6 seconds"><i /> Live</span>
      </header>

      <div className="layout">
        <main className="queue">
          <Upload onActivity={refresh} onBatch={(id) => { setBatchId(id); setFilter("batch"); }} />

          <section aria-labelledby="queue-h">
            <div className="q-head">
              <div>
                <h1 id="queue-h">Review queue</h1>
                <p className="q-sub">Highest risk first. The score ranks review order; it never decides a claim.</p>
              </div>
              <label className="search">
                <SearchIcon />
                <input type="search" placeholder="Search claimant or policy" value={query} onChange={(e) => setQuery(e.target.value)} />
              </label>
            </div>

            {summary?.total > 0 && (
              <div className="spread" aria-label="Claims by risk band">
                <div className="spread-bar" aria-hidden="true">
                  {["high", "medium", "low", "unscored"].map((k) => summary[k] > 0 && (
                    <i key={k} className={k} style={{ flexGrow: summary[k] }} />
                  ))}
                </div>
                <dl className="spread-key">
                  {["high", "medium", "low", "unscored"].map((k) => (
                    <div key={k} className={k}><dt>{BAND_LABEL[k]}</dt><dd className="num">{(summary[k] ?? 0).toLocaleString()}</dd></div>
                  ))}
                </dl>
              </div>
            )}

            <div className="filters" role="tablist" aria-label="Filter by risk">
              {filters.map((f) => (
                <button key={f} type="button" role="tab" aria-selected={filter === f}
                  className={`chip ${f}${filter === f ? " on" : ""}`} onClick={() => setFilter(f)}>
                  {f === "all" ? "All" : f === "batch" ? "This batch" : BAND_LABEL[f]}
                  <span className="num">{count(f)?.toLocaleString() ?? "–"}</span>
                </button>
              ))}
            </div>

            {error && <p className="inline-error" role="alert">Can't reach the claims service ({error}). Check that the backend is running; this page will keep retrying.</p>}

            {claims === null && !error ? (
              <div className="skeleton" aria-label="Loading claims">{Array.from({ length: 6 }, (_, i) => <i key={i} />)}</div>
            ) : claims?.length === 0 ? (
              <p className="empty">No claims yet. Upload a CSV above and they'll appear here, highest risk first.</p>
            ) : (
              <>
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr><th>Risk</th><th>Claimant</th><th>Policy type</th><th>At fault</th><th>Status</th></tr>
                    </thead>
                    <tbody>
                      {visible.map((c) => (
                        <tr key={c.id} tabIndex={0} aria-selected={c.id === selectedId}
                          className={c.id === selectedId ? "selected" : ""}
                          onClick={() => setSelectedId(c.id)}
                          onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && (e.preventDefault(), setSelectedId(c.id))}>
                          <td><RiskCell claim={c} /></td>
                          <td>{c.claimantName}</td>
                          <td className="muted">{c.features.PolicyType}</td>
                          <td>{c.features.Fault}</td>
                          <td><span className={`status ${c.status.toLowerCase()}`}>{c.status === "UNSCORED" ? "Needs score" : c.status === "SCORED" ? "Scored" : "Scoring…"}</span></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {visible.length === 0 && <p className="empty">Nothing matches that filter.</p>}
                </div>
                {summary && summary.total > claims.length && (
                  <p className="muted foot">Showing the {claims.length} highest-risk of {summary.total.toLocaleString()} claims.</p>
                )}
              </>
            )}
          </section>
        </main>

        {selected && <Detail claim={selected} onClose={() => setSelectedId(null)} />}
      </div>
    </div>
  );
}
