# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users
Fraud and claims investigators triaging a queue of newly filed auto-insurance claims. They work at a desk, in daylight, and need to know which claims to open first and why the model flagged them.

## Product Purpose
InSure! scores claims for fraud risk asynchronously (Kafka, XGBoost with SHAP explanations) and drafts cited investigator briefings (RAG over pgvector with Gemini). Success: an investigator drops in a claims file, watches it process, and works the risk-ranked queue from the top.

## Positioning
Every flag is explainable (SHAP factors) and every briefing cites its sources; scoring never blocks claim intake and degrades to a manual-review state rather than inventing a score when the ML service is down.

## Operating Context
Claims arrive as CSV files (format of `insurance_fraud_data.csv`) or through `POST /api/claims`. Scoring takes roughly 0.2 s per claim and runs on 3 parallel consumers. Claims the model could not score are marked Unscored and retried automatically.

## Capabilities and Constraints
- CSV upload at `POST /api/batches` (max 25 MB / 25,000 rows); required columns: age_of_driver, gender, accident_site, claim_date, total_claim.
- Rows carry no claimant name or policy number; the app labels them by file line.
- Briefings need the rag-service and a Gemini API key.
- Document (PDF/image) upload is out of scope.

## Evidence on Hand
Dataset: `insurance_fraud_data.csv` (12K claims). Model metrics are modest (ROC-AUC ~0.63); do not present the score as proof of fraud. No customer testimonials or benchmarks exist; do not invent any.

## Product Principles
- The score prioritises review; it never decides a claim.
- Show why before showing what to do.
- Be honest about state: unscored is not low risk.
- Intake is never blocked by scoring.
