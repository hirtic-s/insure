// Scores are probabilities against a ~6% base rate: most claims sit below 20%, so the
// distribution and the per-claim scale use a square-root axis to keep that range readable.
export const BASE_RATE = 0.06;

export function makeScale(maxScore) {
  const domain = Math.min(1, Math.max(0.5, (maxScore ?? 0) * 1.1));
  const root = Math.sqrt(domain);
  const x = (s) => Math.sqrt(Math.min(Math.max(s, 0), domain)) / root; // 0..1 along the axis
  const inverse = (f) => (f * root) ** 2;
  const ticks = [0, 0.05, 0.1, 0.25, 0.5, 1].filter((t) => t <= domain);
  return { x, inverse, ticks, domain };
}

export const tickLabel = (t) => (t === 0 ? "0" : `${Math.round(t * 100)}%`);
