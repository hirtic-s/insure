# InSure! ML Fraud Scoring Service

XGBoost fraud-risk model served by FastAPI. For each claim it returns a fraud
probability, a risk band (HIGH / MEDIUM / LOW) and the top SHAP factors in plain English.

Trained on `data/fraud_oracle.csv` (Kaggle "Vehicle Insurance Fraud Detection": 15,420 claims, about 6% fraud).

## Setup, train, run

```bash
cd ml-service
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
python train_model.py          # writes model/*.joblib and column_metadata.json
uvicorn app:app --port 8000
```

`GET /schema` lists the 28 input columns and their allowed values. `POST /score` takes a flat
JSON object with those columns and returns `risk_score`, `risk_band` and `top_factors`.

## How good is it

Five-fold cross-validated, out-of-fold predictions (`model/column_metadata.json` holds the numbers):

| Metric | Value |
|---|---|
| ROC-AUC | 0.829 |
| PR-AUC (random = 0.060) | 0.232 |
| Fraud found in top 10% of claims by score | 36% (precision 21%) |
| Fraud found in top 30% of claims by score | 76% |

This ranks claims for review; it does not decide them. Most High-band claims are still legitimate.
Bands come from the score distribution: High is the top 10% of claims (score of about 16% or more),
Medium the next 20% (about 8% or more). Scores are close to real probabilities because training does
not reweight classes, against a base rate of about 6%.

## Data decisions

| Column | Decision | Why |
|---|---|---|
| `PolicyNumber`, `RepNumber` | dropped | Row identifiers; they encode file position, not the claim. |
| `Year` | dropped | Only 1994-1996, a dataset artefact. Keeping it inflates CV AUC to about 0.85 with no real signal. |
| `NumberOfSuppliments` | dropped | Added after a claim is filed, so unavailable at scoring time. Dropping it did not change AUC. |
| `Fault` | kept | Known at filing and the strongest signal (7.9% fraud when the policyholder is at fault, 0.9% for a third party). Without it AUC falls to about 0.73. If your process only decides fault during investigation, drop it and expect the lower number. |

Other caveats: the label column is only 925 fraud cases; `Age` is 0 for about 300 rows (a missing-value
placeholder in the source); and scoring rows that were in the training data gives optimistic results, so
evaluate on held-out or out-of-fold predictions only.

## Earlier dataset

The first version trained on a 12K-claim file that, tested the same way, topped out at ROC-AUC about 0.65 even
with more tuning and with its leakage columns added back, so the labels carried little signal.
