"""
app.py — FastAPI fraud-scoring service.

Loads the trained XGBoost model, OneHotEncoder and SHAP explainer on startup,
then exposes POST /score: a fraud probability, a risk band, and the top
human-readable SHAP factors for that claim.
"""

import json
import os
from contextlib import asynccontextmanager
from typing import Any

import joblib
import numpy as np
from fastapi import Body, FastAPI, HTTPException

MODEL_DIR = os.path.join(os.path.dirname(__file__), "model")
ml_artifacts: dict = {}

# Plain-English names for the dataset's columns, shown to claim reviewers.
COLUMN_LABELS = {
    "Month": "Accident month",
    "WeekOfMonth": "Accident week of month",
    "DayOfWeek": "Accident day",
    "Make": "Vehicle make",
    "AccidentArea": "Accident area",
    "DayOfWeekClaimed": "Claim filed on",
    "MonthClaimed": "Claim filed in",
    "WeekOfMonthClaimed": "Claim filed week of month",
    "Sex": "Driver sex",
    "MaritalStatus": "Marital status",
    "Age": "Driver age",
    "Fault": "At fault",
    "PolicyType": "Policy type",
    "VehicleCategory": "Vehicle category",
    "VehiclePrice": "Vehicle price",
    "Deductible": "Deductible",
    "DriverRating": "Driver rating",
    "Days_Policy_Accident": "Days from policy start to accident",
    "Days_Policy_Claim": "Days from policy start to claim",
    "PastNumberOfClaims": "Past claims",
    "AgeOfVehicle": "Vehicle age",
    "AgeOfPolicyHolder": "Policyholder age",
    "PoliceReportFiled": "Police report filed",
    "WitnessPresent": "Witness present",
    "AgentType": "Agent type",
    "AddressChange_Claim": "Address change before claim",
    "NumberOfCars": "Cars on policy",
    "BasePolicy": "Base policy",
}


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


def risk_band(score: float) -> str:
    m = ml_artifacts["metadata"]["metrics"]
    if score >= m["high_min"]:
        return "HIGH"
    if score >= m["medium_min"]:
        return "MEDIUM"
    return "LOW"


def _factor_label(column: str, value: Any, shap_value: float) -> str:
    if isinstance(value, float) and value.is_integer():
        value = int(value)
    direction = "increases" if shap_value > 0 else "decreases"
    # The trailing [+0.82] is the SHAP value (log-odds); the UI draws it as a bar.
    return f"{COLUMN_LABELS.get(column, column)}: {value} ({direction} risk) [{shap_value:+.2f}]"


@app.post("/score")
def score(claim: dict[str, Any] = Body(...)):
    """Body: a flat JSON object with every column listed in /schema."""
    meta = ml_artifacts["metadata"]
    numeric_cols, categorical_cols = meta["numeric_cols"], meta["categorical_cols"]

    missing = [c for c in numeric_cols + categorical_cols if claim.get(c) in (None, "")]
    if missing:
        raise HTTPException(status_code=422, detail=f"Missing fields: {', '.join(missing)}")
    try:
        numeric_vec = np.array([[float(claim[c]) for c in numeric_cols]])
    except (TypeError, ValueError) as e:
        raise HTTPException(status_code=422, detail=f"Non-numeric value in a numeric field: {e}")

    encoded_vec = ml_artifacts["encoder"].transform([[str(claim[c]) for c in categorical_cols]])
    X_input = np.hstack([numeric_vec, encoded_vec])

    risk_score = float(ml_artifacts["model"].predict_proba(X_input)[0, 1])
    shap_values = ml_artifacts["explainer"].shap_values(X_input)[0]

    # Only features that describe this claim: numeric columns, and the one-hot
    # column matching the claim's own category. An inactive one-hot column
    # ("not Third Party") repeats information the active one already carries.
    candidates = []
    for i, name in enumerate(meta["all_feature_names"]):
        if name in numeric_cols:
            candidates.append((i, name, float(claim[name])))
        else:
            col = next(c for c in sorted(categorical_cols, key=len, reverse=True) if name.startswith(c + "_"))
            value = name[len(col) + 1:]
            if str(claim[col]) == value:
                candidates.append((i, col, value))

    top = sorted(candidates, key=lambda t: abs(shap_values[t[0]]), reverse=True)[:5]
    top_factors = [_factor_label(col, val, shap_values[i]) for i, col, val in top]

    return {
        "risk_score": round(risk_score, 4),
        "risk_band": risk_band(risk_score),
        "top_factors": top_factors,
    }


@app.get("/schema")
def schema():
    meta = ml_artifacts["metadata"]
    return {
        "numeric": meta["numeric_cols"],
        "categorical": meta["category_values"],
        "metrics": meta["metrics"],
    }


@app.get("/health")
def health():
    return {"status": "ok"}
