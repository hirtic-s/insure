export const money = (n) =>
  n == null ? "—" : Number(n).toLocaleString("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 0 });

// Scores are probabilities against a ~6% base rate, so low values keep a decimal.
export const pct = (score) => (score < 0.1 ? (score * 100).toFixed(1) : String(Math.round(score * 100)));

// Bands are assigned by the ML service from its score distribution.
export function band(claim) {
  return claim.riskBand ? claim.riskBand.toLowerCase() : "unscored";
}

export const BAND_LABEL = { high: "High", medium: "Medium", low: "Low", unscored: "Unscored" };

export function duration(ms) {
  if (ms == null) return "—";
  if (ms < 1000) return `${ms} ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)} s`;
  return `${Math.floor(ms / 60000)} m ${Math.round((ms % 60000) / 1000)} s`;
}

export function factorParts(text) {
  const m = text.match(/^(.*) \((increases|decreases) risk\)(?: \[([+-]?\d+(?:\.\d+)?)\])?$/);
  return m ? { label: m[1], up: m[2] === "increases", weight: m[3] == null ? null : Math.abs(Number(m[3])) } : { label: text, up: null, weight: null };
}

// Facts a reviewer would check by hand, read straight from the claim. They are prompts to look, never evidence of fraud.
export function redFlags(f) {
  const flags = [];
  if (f.Fault === "Policy Holder") flags.push("Policyholder is at fault");
  if (f.PoliceReportFiled === "No") flags.push("No police report filed");
  if (f.WitnessPresent === "No") flags.push("No witness");
  if (f.PastNumberOfClaims === "2 to 4" || f.PastNumberOfClaims === "more than 4") flags.push(`Past claims: ${f.PastNumberOfClaims}`);
  if (f.AddressChange_Claim === "under 6 months" || f.AddressChange_Claim === "1 year") flags.push(`Address changed ${f.AddressChange_Claim === "1 year" ? "about a year" : "under 6 months"} before the claim`);
  if (f.Days_Policy_Accident === "1 to 7") flags.push("Accident within a week of the policy starting");
  return flags;
}

export const ratio = (score, base) => { const r = score / base; return r >= 10 ? Math.round(r) : r.toFixed(1); };
