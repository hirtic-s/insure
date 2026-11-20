import { useEffect, useMemo, useRef, useState } from "react";
import { makeScale, tickLabel } from "./scale.js";
import { pct } from "./format.js";

const H = 176;
const M = { top: 30, right: 4, bottom: 52, left: 4 };
const BINS = 44;

function useWidth() {
  const ref = useRef(null);
  const [w, setW] = useState(0);
  useEffect(() => {
    const ro = new ResizeObserver(([e]) => setW(Math.floor(e.contentRect.width)));
    ro.observe(ref.current);
    return () => ro.disconnect();
  }, []);
  return [ref, w];
}

// Rounded-top bar anchored to the baseline.
const bar = (x, y, w, h, r) =>
  `M${x},${y + h}V${y + r}Q${x},${y} ${x + r},${y}H${x + w - r}Q${x + w},${y} ${x + w},${y + r}V${y + h}Z`;

export default function Spectrum({ points, summary, selected, batchId }) {
  const [ref, W] = useWidth();
  const [hover, setHover] = useState(null);

  const scale = useMemo(() => makeScale(Math.max(0, ...points.map((p) => p.riskScore))), [points]);
  const plotW = Math.max(0, W - M.left - M.right);
  const plotH = H - M.top - M.bottom;
  const base = M.top + plotH;

  const bins = useMemo(() => {
    const counts = new Array(BINS).fill(0);
    for (const p of points) counts[Math.min(BINS - 1, Math.floor(scale.x(p.riskScore) * BINS))]++;
    return counts;
  }, [points, scale]);
  const maxCount = Math.max(1, ...bins);

  const px = (score) => M.left + scale.x(score) * plotW;
  const bandOf = (score) =>
    summary?.highMin != null && score >= summary.highMin ? "high"
      : summary?.mediumMin != null && score >= summary.mediumMin ? "medium" : "low";

  const batchPoints = batchId ? points.filter((p) => p.batchId === batchId) : [];
  const barW = plotW / BINS;

  return (
    <figure className="spectrum" ref={ref}>
      <figcaption className="sr-only">
        Distribution of fraud risk scores across {points.length} scored claims. {summary?.high ?? 0} are in the high band.
      </figcaption>
      {W > 0 && (
        <svg width={W} height={H} role="img" aria-label="Score distribution" onMouseLeave={() => setHover(null)}>
          {/* band cutoffs, labelled directly */}
          {[["medium", summary?.mediumMin, "Medium"], ["high", summary?.highMin, "High"]].map(([k, v, label]) =>
            v == null ? null : (
              <g key={k} className={`cut ${k}`}>
                <line x1={px(v)} x2={px(v)} y1={M.top - 8} y2={base} />
                <text x={px(v) + 6} y={M.top - 12}>{label} · {pct(v)}%+</text>
              </g>
            )
          )}

          {/* bars */}
          {bins.map((count, i) => {
            if (!count) return null;
            const h = Math.max(4, (count / maxCount) * plotH);
            const mid = scale.inverse((i + 0.5) / BINS);
            return (
              <path key={i} className={`bin ${bandOf(mid)}`} d={bar(M.left + i * barW + 1, base - h, Math.max(1, barW - 2), h, 3)}
                style={{ animationDelay: `${i * 9}ms` }} />
            );
          })}

          {/* baseline + axis */}
          <line className="axis" x1={M.left} x2={M.left + plotW} y1={base} y2={base} />
          {scale.ticks.map((t) => (
            <g key={t} className="tick">
              <line x1={px(t)} x2={px(t)} y1={base} y2={base + 4} />
              <text x={px(t)} y={base + 17} textAnchor={t === 0 ? "start" : "middle"}>{tickLabel(t)}</text>
            </g>
          ))}

          {/* this batch: a dot per claim under the axis */}
          {batchPoints.map((p) => (
            <circle key={p.id} className="dot" cx={px(p.riskScore)} cy={base + 34} r="4" />
          ))}

          {/* selected claim */}
          {selected?.riskScore != null && (
            <g className="pin" style={{ transform: `translateX(${px(selected.riskScore)}px)` }}>
              <line x1="0" x2="0" y1={M.top - 2} y2={base} />
              <path d={`M-5,${M.top - 10}H5L0,${M.top - 2}Z`} />
            </g>
          )}

          {/* hover columns (larger than the marks) */}
          {bins.map((count, i) => (
            <rect key={i} className="hit" x={M.left + i * barW} y={M.top - 8} width={barW} height={plotH + 8}
              onMouseEnter={() => setHover({ i, count, lo: scale.inverse(i / BINS), hi: scale.inverse((i + 1) / BINS) })} />
          ))}
        </svg>
      )}

      {hover && (
        <div className="tip" style={{ left: Math.min(Math.max(M.left + (hover.i + 0.5) * barW, 70), W - 70) }}>
          <strong className="num">{hover.count.toLocaleString()}</strong> {hover.count === 1 ? "claim" : "claims"}
          <span className="num"> · {pct(hover.lo)}–{pct(hover.hi)}%</span>
        </div>
      )}

      {(batchPoints.length > 0 || selected?.riskScore != null) && (
        <div className="legend">
          {batchPoints.length > 0 && <span><i className="k dot" /> This batch</span>}
          {selected?.riskScore != null && <span><i className="k pin" /> Selected claim</span>}
        </div>
      )}
    </figure>
  );
}
