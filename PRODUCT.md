# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users
Fraud and claims investigators triaging a queue of newly filed auto-insurance claims. They work at a desk, in daylight, and need to know which claims to open first and why the model flagged them.

## Product Purpose
InSure! scores claims for fraud risk asynchronously (Kafka, XGBoost with SHAP explanations). Success: an investigator drops in a claims file (or enters claims by hand), watches it process, and works the risk-ranked queue from the top.

## Positioning
Every flag is explainable (SHAP factors), and scoring never blocks claim intake and degrades to a manual-review state rather than inventing a score when the ML service is down.

## Operating Context
Claims arrive as CSV files (columns of `fraud_oracle.csv`, minus the label) or through `POST /api/claims` with a `features` object. Scoring takes roughly 0.2 s per claim and runs on 3 parallel consumers. Claims the model could not score are marked Unscored and retried automatically.

## Capabilities and Constraints
- CSV upload at `POST /api/batches` (max 25 MB / 25,000 rows); the 28 model columns are required (see `ml-service` `/schema`).
- Rows carry no claimant name or policy number; the app labels them by file line.
- Document (PDF/image) upload is out of scope.

## Evidence on Hand
Dataset: `ml-service/data/fraud_oracle.csv` (15.4K claims, ~6% fraud). Cross-validated ROC-AUC 0.83; the top 10% of claims by score hold about 36% of fraud (precision ~21%). Do not present the score as proof of fraud. No customer testimonials or benchmarks exist; do not invent any.

## Product Principles
- The score prioritises review; it never decides a claim.
- Show why before showing what to do.
- Be honest about state: unscored is not low risk.
- Intake is never blocked by scoring.
