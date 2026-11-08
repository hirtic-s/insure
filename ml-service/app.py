"""
app.py — FastAPI fraud-scoring service.

Loads a pre-trained XGBoost model, OneHotEncoder, and SHAP explainer on
startup, then exposes POST /score to return a risk score + human-readable
explanations for each prediction.
"""

import json
import os
from contextlib import asynccontextmanager

import joblib
import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

# ── human-readable label mapping ─────────────────────────────────────────
# Maps raw feature names (including one-hot suffixes) to plain-English
# descriptions shown to claim reviewers.  If a feature isn't listed here
# we fall back to a cleaned-up version of the column name.
FEATURE_LABELS = {
    # numeric
    "age_of_driver":       "Driver age",
    "safety_rating":       "Safety rating of the vehicle",
    "annual_income":       "Claimant annual income",
    "high_education":      "Education level indicator",
    "address_change":      "Recent address change",
    "past_num_of_claims":  "Number of past claims",
    "witness_present":     "Witness present at scene",
    "police_report":       "Police report filed",
    "age_of_vehicle":      "Vehicle age",
    "vehicle_price":       "Vehicle price",
    "total_claim":         "Total claim amount",
    "injury_claim":        "Injury claim amount",
    "policy deductible":   "Policy deductible",
    "annual premium":      "Annual premium amount",
    # categorical (one-hot) — a sample; unlisted ones get auto-cleaned
    "gender_M":                        "Male driver",
    "gender_F":                        "Female driver",
    "property_status_Rent":            "Claimant rents property",
    "property_status_Own":             "Claimant owns property",
    "accident_site_Highway":           "Accident occurred on highway",
    "accident_site_Local":             "Accident on local road",
    "accident_site_Parking Lot":       "Accident in parking lot",
    "channel_Online":                  "Claim filed online",
    "channel_Phone":                   "Claim filed by phone",
    "channel_Broker":                  "Claim filed via broker",
    "vehicle_category_Compact":        "Compact vehicle",
    "vehicle_category_Large":          "Large vehicle",
    "vehicle_category_Medium":         "Medium vehicle",
    "marital_status_0":                "Unmarried claimant",
    "marital_status_1":                "Married claimant",
}


def _human_label(feature_name: str, shap_value: float) -> str:
    """Turn a raw feature name + SHAP value into a readable sentence."""
    label = FEATURE_LABELS.get(feature_name)
    if label is None:
        # Auto-clean: "vehicle_color_silver" → "Vehicle color: silver"
        parts = feature_name.split("_", 1)
        if len(parts) == 2:
            label = f"{parts[0].replace('_', ' ').title()}: {parts[1]}"
        else:
            label = feature_name.replace("_", " ").title()

    direction = "increases" if shap_value > 0 else "decreases"
    return f"{label} ({direction} risk)"


# ── pydantic request model ───────────────────────────────────────────────
class ScoreRequest(BaseModel):
    age_of_driver: float = Field(..., ge=16, le=120)
    gender: str = Field(..., pattern="^(M|F)$")
    marital_status: str
    safety_rating: float
    annual_income: float
    high_education: int = Field(..., ge=0, le=1)
    address_change: int = Field(..., ge=0, le=1)
    property_status: str
    claim_day_of_week: str
    accident_site: str
    past_num_of_claims: int = Field(..., ge=0)
    witness_present: int = Field(..., ge=0, le=1)
    channel: str
    police_report: int = Field(..., ge=0, le=1)
    age_of_vehicle: float
    vehicle_category: str
    vehicle_price: float
    vehicle_color: str
    total_claim: float
    injury_claim: float
    policy_deductible: float = Field(..., alias="policy deductible")
    annual_premium: float = Field(..., alias="annual premium")

    model_config = {"populate_by_name": True}


# ── app lifecycle ────────────────────────────────────────────────────────
MODEL_DIR = os.path.join(os.path.dirname(__file__), "model")

ml_artifacts: dict = {}


@asynccontextmanager
async def lifespan(application: FastAPI):
    ml_artifacts["model"] = joblib.load(os.path.join(MODEL_DIR, "xgb_model.joblib"))
    ml_artifacts["encoder"] = joblib.load(os.path.join(MODEL_DIR, "onehot_encoder.joblib"))
    ml_artifacts["explainer"] = joblib.load(os.path.join(MODEL_DIR, "shap_explainer.joblib"))
    with open(os.path.join(MODEL_DIR, "column_metadata.json")) as f:
        ml_artifacts["metadata"] = json.load(f)
    print("ML artefacts loaded.")
    yield
    ml_artifacts.clear()


app = FastAPI(title="InSure! Fraud Scoring Service", lifespan=lifespan)


# ── scoring endpoint ─────────────────────────────────────────────────────
@app.post("/score")
def score(req: ScoreRequest):
    metadata = ml_artifacts["metadata"]
    numeric_cols = metadata["numeric_cols"]
    categorical_cols = metadata["categorical_cols"]
    all_feature_names = metadata["all_feature_names"]
    encoder = ml_artifacts["encoder"]
    model = ml_artifacts["model"]
    explainer = ml_artifacts["explainer"]

    # Build a dict mapping original column names to values.
    # Pydantic aliases let the JSON use spaces, but internally we need the
    # original column names that match the training data.
    raw = {
        "age_of_driver": req.age_of_driver,
        "gender": req.gender,
        "marital_status": req.marital_status,
        "safety_rating": req.safety_rating,
        "annual_income": req.annual_income,
        "high_education": req.high_education,
        "address_change": req.address_change,
        "property_status": req.property_status,
        "claim_day_of_week": req.claim_day_of_week,
        "accident_site": req.accident_site,
        "past_num_of_claims": req.past_num_of_claims,
        "witness_present": req.witness_present,
        "channel": req.channel,
        "police_report": req.police_report,
        "age_of_vehicle": req.age_of_vehicle,
        "vehicle_category": req.vehicle_category,
        "vehicle_price": req.vehicle_price,
        "vehicle_color": req.vehicle_color,
        "total_claim": req.total_claim,
        "injury_claim": req.injury_claim,
        "policy deductible": req.policy_deductible,
        "annual premium": req.annual_premium,
    }

    # Numeric vector (same column order as training)
    numeric_vec = np.array([[raw[c] for c in numeric_cols]])

    # One-hot encode categoricals
    cat_values = [[raw[c] for c in categorical_cols]]
    encoded_vec = encoder.transform(cat_values)

    # Combine
    X_input = np.hstack([numeric_vec, encoded_vec])

    # Predict fraud probability
    risk_score = float(model.predict_proba(X_input)[0, 1])

    # SHAP explanation for this single prediction
    shap_values = explainer.shap_values(X_input)[0]

    # Pick top 5 features by absolute SHAP contribution
    top_indices = np.argsort(np.abs(shap_values))[::-1][:5]
    top_factors = [
        _human_label(all_feature_names[i], shap_values[i])
        for i in top_indices
    ]

    return {"risk_score": round(risk_score, 4), "top_factors": top_factors}


@app.get("/health")
def health():
    return {"status": "ok"}
