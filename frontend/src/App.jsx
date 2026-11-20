import { useCallback, useEffect, useMemo, useState } from "react";
import { getClaims, getSummary } from "./api.js";
import Upload from "./Upload.jsx";
import Detail from "./Detail.jsx";
import { SearchIcon } from "./icons.jsx";
import { BAND_LABEL, band, money, pct } from "./format.js";

const FILTERS = ["all", "high", "medium", "low", "unscored"];

function RiskCell({ score }) {
  const b = band(score);
  return (
    <span className={`risk ${b}`}>
      <span className="num">{score == null ? "—" : `${pct(score)}%`}</span>
      <span className="meter" aria-hidden="true"><i style={{ width: `${score == null ? 0 : pct(score)}%` }} /></span>
    </span>
  );
}

export default function App() {
  const [claims, setClaims] = useState(null);
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [filter, setFilter] = useState("all");
  const [query, setQuery] = useState("");

  const refresh = useCallback(async () => {
    try {
      const [c, s] = await Promise.all([getClaims(), getSummary()]);
      setClaims(c);
      setSummary(s);
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    refresh();
    const t = setInterval(refresh, 6000);
    return () => clearInterval(t);
  }, [refresh]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (claims ?? []).filter(
      (c) =>
        (filter === "all" || band(c.riskScore) === filter) &&
        (!q || c.claimantName.toLowerCase().includes(q) || c.policyNumber.toLowerCase().includes(q))
    );
  }, [claims, filter, query]);

  const selected = claims?.find((c) => c.id === selectedId);
  const count = (f) => (f === "all" ? summary?.total : summary?.[f]);

  return (
    <div className={`app${selected ? " has-selection" : ""}`}>
      <header className="topbar">
        <span className="wordmark">InSure<span>!</span></span>
        <span className="muted">Claims review</span>
      </header>

      <div className="layout">
        <main className="queue">
          <Upload onActivity={refresh} />

          <section aria-labelledby="queue-h">
            <div className="q-head">
              <h1 id="queue-h">Review queue</h1>
              <label className="search">
                <SearchIcon />
                <input type="search" placeholder="Search claimant or policy" value={query} onChange={(e) => setQuery(e.target.value)} />
              </label>
            </div>

            <div className="filters" role="tablist" aria-label="Filter by risk">
              {FILTERS.map((f) => (
                <button key={f} type="button" role="tab" aria-selected={filter === f}
                  className={`chip ${f}${filter === f ? " on" : ""}`} onClick={() => setFilter(f)}>
                  {f === "all" ? "All" : BAND_LABEL[f]}
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
                      <tr><th>Risk</th><th>Claimant</th><th>Policy</th><th className="r">Amount</th><th>Status</th></tr>
                    </thead>
                    <tbody>
                      {visible.map((c) => (
                        <tr key={c.id} tabIndex={0} aria-selected={c.id === selectedId}
                          className={c.id === selectedId ? "selected" : ""}
                          onClick={() => setSelectedId(c.id)}
                          onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && (e.preventDefault(), setSelectedId(c.id))}>
                          <td><RiskCell score={c.riskScore} /></td>
                          <td>{c.claimantName}</td>
                          <td className="muted">{c.policyNumber}</td>
                          <td className="r num">{money(c.claimAmount)}</td>
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
