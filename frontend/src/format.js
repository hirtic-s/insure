export const money = (n) =>
  n == null ? "—" : Number(n).toLocaleString("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 0 });

export const pct = (score) => Math.round(score * 100);

// Thresholds mirror ClaimController.summary on the backend.
export function band(score) {
  if (score == null) return "unscored";
  if (score >= 0.6) return "high";
  if (score >= 0.35) return "medium";
  return "low";
}

export const BAND_LABEL = { high: "High", medium: "Medium", low: "Low", unscored: "Unscored" };

export function duration(ms) {
  if (ms == null) return "—";
  if (ms < 1000) return `${ms} ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)} s`;
  return `${Math.floor(ms / 60000)} m ${Math.round((ms % 60000) / 1000)} s`;
}

export function factorParts(text) {
  const m = text.match(/^(.*) \((increases|decreases) risk\)$/);
  return m ? { label: m[1], up: m[2] === "increases" } : { label: text, up: null };
}
