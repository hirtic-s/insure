# InSure! ML Fraud Scoring Service

XGBoost-based fraud detection model exposed as a FastAPI microservice.
Returns a risk score (0-1) and human-readable SHAP explanations for each claim.

## Setup

```bash
cd ml-service
python3 -m venv venv
source venv/bin/activate
pip install fastapi uvicorn xgboost scikit-learn pandas numpy shap joblib
```

## Train the model

```bash
python train_model.py
```

This reads `data/insurance_fraud_data.csv`, trains an XGBoost classifier, and
saves artefacts (model, encoder, SHAP explainer, column metadata) to `model/`.

## Start the service

```bash
uvicorn app:app --reload --port 8000
```

## Score a claim

```bash
curl -X POST http://localhost:8000/score \
  -H "Content-Type: application/json" \
  -d '{
    "age_of_driver": 35,
    "gender": "M",
    "marital_status": "1",
    "safety_rating": 60,
    "annual_income": 45000,
    "high_education": 1,
    "address_change": 0,
    "property_status": "Own",
    "claim_day_of_week": "Monday",
    "accident_site": "Highway",
    "past_num_of_claims": 2,
    "witness_present": 0,
    "channel": "Online",
    "police_report": 0,
    "age_of_vehicle": 5,
    "vehicle_category": "Medium",
    "vehicle_price": 20000,
    "vehicle_color": "black",
    "total_claim": 30000,
    "injury_claim": 5000,
    "policy deductible": 500,
    "annual premium": 1200
  }'
```

Response:

```json
{
  "risk_score": 0.2356,
  "top_factors": [
    "Claimant annual income (decreases risk)",
    "Driver age (decreases risk)",
    "Injury claim amount (decreases risk)",
    "Vehicle price (decreases risk)",
    "Education level indicator (increases risk)"
  ]
}
```

## Health check

```
GET http://localhost:8000/health
```

## Data Leakage Note

**What is data leakage?** It occurs when a model is trained on features that
would not be available at prediction time in production. The model learns
patterns that can't be reproduced when scoring real claims, giving
artificially inflated (or misleading) metrics during evaluation.

**Features removed:**

| Feature | Why it leaks |
|---|---|
| `days open` | Only known after a claim has been open for some time -- doesn't exist at the moment a new claim is submitted for scoring. |
| `form defects` | Counted during manual review of the submitted paperwork, not available at filing time. |
| `liab_prct` | Liability percentage is determined by a claims adjuster during investigation, not reported by the claimant. |

**Impact on metrics:**

| Metric | Before (with leakage) | After (clean) |
|---|---|---|
| ROC-AUC | 0.6305 | 0.6357 |
| Fraud precision | 0.32 | 0.34 |
| Fraud recall | 0.44 | 0.47 |
| Fraud F1 | 0.37 | 0.39 |

In this case, performance actually improved slightly after removing the leaked
features. The leaked features were post-hoc signals that added noise rather
than clean predictive signal. This won't always happen -- in many real-world
cases, removing leakage *does* reduce apparent performance, which is correct
because the original metrics were artificially inflated.
