import { Fragment, useState } from "react";
import { draftBriefing } from "./api.js";

// Renders the model's "## heading" / "- bullet" text, turning [S1] and [C] tags into citation chips.
function Cited({ text, onCite }) {
  return text.split(/(\[(?:S\d+|C)\])/g).map((part, i) => {
    const tag = part.match(/^\[(S\d+|C)\]$/)?.[1];
    if (!tag) return <Fragment key={i}>{part}</Fragment>;
    return tag === "C"
      ? <sup key={i} className="cite claim" title="From the claim record">C</sup>
      : <button key={i} type="button" className="cite" onClick={() => onCite(tag)} aria-label={`Source ${tag.slice(1)}`}>{tag.slice(1)}</button>;
  });
}

function Body({ text, onCite }) {
  const blocks = [];
  for (const line of text.split("\n")) {
    const t = line.trim();
    if (!t) continue;
    if (t.startsWith("## ")) blocks.push({ h: t.slice(3) });
    else if (/^[-*] /.test(t)) {
      const last = blocks[blocks.length - 1];
      if (last?.items) last.items.push(t.slice(2)); else blocks.push({ items: [t.slice(2)] });
    } else blocks.push({ p: t });
  }
  return blocks.map((b, i) =>
    b.h ? <h4 key={i}>{b.h}</h4>
    : b.items ? <ul key={i}>{b.items.map((x, j) => <li key={j}><Cited text={x} onCite={onCite} /></li>)}</ul>
    : <p key={i}><Cited text={b.p} onCite={onCite} /></p>);
}

export default function Briefing({ claimId }) {
  const [state, setState] = useState({ phase: "idle" });
  const [open, setOpen] = useState(null);

  const run = async (refresh) => {
    setState((s) => ({ ...s, phase: "loading", error: null }));
    try {
      setState({ phase: "done", ...(await draftBriefing(claimId, refresh)) });
    } catch (e) {
      setState((s) => ({ ...s, phase: s.briefing ? "done" : "idle", error: e.message === "Failed to fetch" ? "Can't reach the claims service." : e.message }));
    }
  };

  const { phase, briefing, citations = [], cached, error } = state;
  const cite = (tag) => { setOpen(tag); document.getElementById(`src-${claimId}-${tag}`)?.scrollIntoView({ block: "nearest", behavior: "smooth" }); };

  return (
    <section className="block briefing" aria-labelledby={`brief-${claimId}`}>
      <div className="b-head">
        <h3 id={`brief-${claimId}`}>Briefing</h3>
        {phase === "done" && <button type="button" className="btn ghost b-redo" onClick={() => run(true)} disabled={phase === "loading"}>Redraft</button>}
      </div>

      {phase === "idle" && (
        <>
          <p className="f-note">Drafts a short briefing from this claim and your investigation guidance, with each statement cited. It is a starting point to check, not a finding.</p>
          <button type="button" className="btn primary b-go" onClick={() => run(false)}>Draft briefing</button>
        </>
      )}
      {phase === "loading" && <p className="b-wait" role="status"><i className="spinner" aria-hidden="true" /> Reading the guidance and drafting. This takes a few seconds.</p>}
      {error && <p className="inline-error" role="alert">{error}</p>}

      {briefing && (
        <div className="b-body" aria-busy={phase === "loading"}>
          <Body text={briefing} onCite={cite} />
          {citations.length > 0 && (
            <div className="sources">
              <h3>Sources</h3>
              {citations.map((c) => (
                <details key={c.tag} id={`src-${claimId}-${c.tag}`} open={open === c.tag} onToggle={(e) => e.currentTarget.open ? setOpen(c.tag) : open === c.tag && setOpen(null)}>
                  <summary><span className="cite static">{c.tag.slice(1)}</span> {c.title}</summary>
                  <p>{c.content.split("\n").slice(1).join(" ")}</p>
                </details>
              ))}
            </div>
          )}
          <p className="f-note">AI-drafted{cached ? " (saved earlier)" : ""}. Check it against the claim before relying on it.</p>
        </div>
      )}
    </section>
  );
}
