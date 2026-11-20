# Interpreting the fraud risk score

The risk score is the XGBoost model's estimated probability that a claim is fraudulent, and the listed factors are SHAP contributions explaining that score.

- A high score is a prioritisation signal, not a finding. It ranks where reviewer time is best spent.
- "Increases risk" factors are the features pushing this claim's score up relative to the typical claim; "decreases risk" factors push it down.
- Several demographic or profile features can appear as factors. Never deny or reduce a claim on those alone; base decisions on documented evidence from the investigation.
- Claims with no score (UNSCORED) were not scored because the scoring service was unavailable and must be reviewed manually or after the automatic retry.
