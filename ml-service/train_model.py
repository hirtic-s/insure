"""
train_model.py — Train an XGBoost fraud-detection classifier and persist
the model, encoder, and SHAP explainer so they can be loaded by the
FastAPI service without retraining.

Preprocessing choices are explained inline.
"""

import os
import json
import numpy as np
import pandas as pd
import joblib
import shap
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import OneHotEncoder
from sklearn.metrics import classification_report, roc_auc_score
from xgboost import XGBClassifier

# ── paths ────────────────────────────────────────────────────────────────
DATA_PATH = os.path.join(os.path.dirname(__file__), "data", "insurance_fraud_data.csv")
MODEL_DIR = os.path.join(os.path.dirname(__file__), "model")
os.makedirs(MODEL_DIR, exist_ok=True)

# ── 1. load ──────────────────────────────────────────────────────────────
df = pd.read_csv(DATA_PATH)
print(f"Loaded {len(df)} rows, {df.shape[1]} columns")

# ── 2. drop claim_date ──────────────────────────────────────────────────
# Raw date strings aren't directly useful as a feature. The dataset already
# provides claim_day_of_week, which captures the weekday signal (e.g.
# claims filed on weekends may correlate with fraud). Dropping avoids
# leaking exact-date information and keeps the feature set simple.
# ── drop features that cause data leakage ────────────────────────────
# "days open" and "form defects" are only known AFTER a claim is submitted
# and under review — they wouldn't exist at scoring time for a new claim.
# Training on them creates a model that can't be used in production.
#
# "liab_prct" (liability percentage) is determined by a claims adjuster
# during investigation, not reported by the claimant at filing time.
# Including it would also be leakage.
#
# "claim_date" is a raw date string; claim_day_of_week already captures
# the weekday signal.
LEAK_COLS = ["claim_date", "days open", "form defects", "liab_prct"]
df = df.drop(columns=LEAK_COLS)

# ── 3. clean sentinel values ────────────────────────────────────────────
# Several columns use "*" as a missing/unknown sentinel.  Replace with NaN
# so pandas can coerce types properly.
df = df.replace("*", np.nan)

# ── 4. separate target ──────────────────────────────────────────────────
TARGET = "fraud reported"
y = df[TARGET]
X = df.drop(columns=[TARGET])

# Drop any rows with NaN target
mask = y.notna()
X, y = X[mask], y[mask].astype(int)

# ── 5. one-hot encode categoricals ──────────────────────────────────────
# Why one-hot instead of label encoding?  XGBoost *can* handle integer-
# encoded categoricals, but one-hot is safer for nominal features with no
# ordinal relationship (e.g. vehicle_color, accident_site).  With only
# ~20 total categories across 8 columns the dimensionality stays low.
CATEGORICAL_COLS = [
    "gender", "marital_status", "property_status", "accident_site",
    "claim_day_of_week", "channel", "vehicle_category", "vehicle_color",
]

# Identify which columns in X are numeric (everything not in CATEGORICAL_COLS)
NUMERIC_COLS = [c for c in X.columns if c not in CATEGORICAL_COLS]

# Coerce numeric columns (some were read as object due to "*" sentinels)
for col in NUMERIC_COLS:
    X[col] = pd.to_numeric(X[col], errors="coerce")

# Fill remaining NaN in numeric columns with the median — a robust choice
# that doesn't shift the distribution the way mean-imputation can.
numeric_medians = X[NUMERIC_COLS].median()
X[NUMERIC_COLS] = X[NUMERIC_COLS].fillna(numeric_medians)

# Fill NaN in categoricals with "Unknown" so OneHotEncoder gets a clean category
for col in CATEGORICAL_COLS:
    X[col] = X[col].fillna("Unknown")

encoder = OneHotEncoder(sparse_output=False, handle_unknown="ignore")
encoded = encoder.fit_transform(X[CATEGORICAL_COLS])
encoded_feature_names = list(encoder.get_feature_names_out(CATEGORICAL_COLS))

# Recombine: numeric columns + one-hot columns
X_numeric = X[NUMERIC_COLS].values
X_processed = np.hstack([X_numeric, encoded])
all_feature_names = NUMERIC_COLS + encoded_feature_names

print(f"Features after encoding: {len(all_feature_names)}")

# ── 5. train/test split ─────────────────────────────────────────────────
# Stratified so both sets preserve the ~25/75 fraud ratio — important
# because a naive random split on an imbalanced dataset could give a test
# set with very few fraud cases, making evaluation noisy.
X_train, X_test, y_train, y_test = train_test_split(
    X_processed, y,
    test_size=0.2,
    random_state=42,
    stratify=y,
)
print(f"Train: {len(X_train)}, Test: {len(X_test)}")

# ── 6. train XGBoost ────────────────────────────────────────────────────
# scale_pos_weight compensates for class imbalance: it tells XGBoost to
# weight positive (fraud) samples more heavily.  The ratio is
# (# negatives / # positives) ≈ 75/25 = 3.0.  Without this, the model
# would be biased toward predicting "not fraud" simply because that class
# dominates.
neg_count = int((y_train == 0).sum())
pos_count = int((y_train == 1).sum())
scale_pos_weight = neg_count / pos_count
print(f"scale_pos_weight = {scale_pos_weight:.2f}  ({neg_count} neg / {pos_count} pos)")

model = XGBClassifier(
    n_estimators=200,
    max_depth=5,
    learning_rate=0.1,
    scale_pos_weight=scale_pos_weight,
    eval_metric="logloss",
    random_state=42,

)
model.fit(X_train, y_train)

# ── 7. evaluate ──────────────────────────────────────────────────────────
y_pred = model.predict(X_test)
y_prob = model.predict_proba(X_test)[:, 1]

print("\n=== Classification Report ===")
print(classification_report(y_test, y_pred, target_names=["Not Fraud", "Fraud"]))

roc_auc = roc_auc_score(y_test, y_prob)
print(f"ROC-AUC: {roc_auc:.4f}\n")

# ── 8. SHAP feature importances ─────────────────────────────────────────
# TreeExplainer is optimised for tree-based models and gives exact Shapley
# values.  We compute on the full test set to get stable global importance.
explainer = shap.TreeExplainer(model)
shap_values = explainer.shap_values(X_test)

# Global importance = mean |SHAP value| per feature
mean_abs_shap = np.abs(shap_values).mean(axis=0)
importance_df = (
    pd.DataFrame({"feature": all_feature_names, "mean_abs_shap": mean_abs_shap})
    .sort_values("mean_abs_shap", ascending=False)
)

print("=== Top 15 Global Feature Importances (mean |SHAP|) ===")
print(importance_df.head(15).to_string(index=False))

# Save importance summary
importance_df.to_csv(os.path.join(MODEL_DIR, "feature_importances.csv"), index=False)

# ── 9. persist artefacts ─────────────────────────────────────────────────
joblib.dump(model, os.path.join(MODEL_DIR, "xgb_model.joblib"))
joblib.dump(encoder, os.path.join(MODEL_DIR, "onehot_encoder.joblib"))
joblib.dump(explainer, os.path.join(MODEL_DIR, "shap_explainer.joblib"))

# Save column metadata so the API knows the exact feature order
metadata = {
    "numeric_cols": NUMERIC_COLS,
    "categorical_cols": CATEGORICAL_COLS,
    "all_feature_names": all_feature_names,
    "numeric_medians": numeric_medians.to_dict(),
}
with open(os.path.join(MODEL_DIR, "column_metadata.json"), "w") as f:
    json.dump(metadata, f, indent=2)

print(f"\nArtefacts saved to {MODEL_DIR}/")
