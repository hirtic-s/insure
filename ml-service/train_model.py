"""
train_model.py — Train the XGBoost fraud-risk model on fraud_oracle.csv and
persist the model, encoder, SHAP explainer and metadata for app.py.

Metrics come from 5-fold cross-validation (out-of-fold predictions), not a
single split, because only ~6% of claims are fraud (about 925 positives).
"""

import json
import os

import joblib
import numpy as np
import pandas as pd
import shap
from sklearn.metrics import average_precision_score, roc_auc_score
from sklearn.model_selection import StratifiedKFold, cross_val_predict
from sklearn.preprocessing import OneHotEncoder
from xgboost import XGBClassifier

HERE = os.path.dirname(__file__)
DATA_PATH = os.path.join(HERE, "data", "fraud_oracle.csv")
MODEL_DIR = os.path.join(HERE, "model")
os.makedirs(MODEL_DIR, exist_ok=True)

TARGET = "FraudFound_P"

# Columns excluded from training, and why.
#   PolicyNumber, RepNumber : row identifiers; they only encode where a row sits
#                             in the file, not anything about the claim.
#   Year                    : 1994-1996 only; a dataset-assembly artefact. With
#                             it, CV AUC reads ~0.85 but the signal is spurious.
#   NumberOfSuppliments     : supplements are added after a claim is filed, so
#                             it is not known at scoring time (dropping it did
#                             not change AUC).
DROP_COLS = ["PolicyNumber", "RepNumber", "Year", "NumberOfSuppliments"]

NUMERIC_COLS = ["WeekOfMonth", "WeekOfMonthClaimed", "Age", "Deductible", "DriverRating"]

df = pd.read_csv(DATA_PATH, encoding="utf-8-sig")
print(f"Loaded {len(df)} rows, fraud rate {df[TARGET].mean():.3%}")

y = df[TARGET].astype(int)
X = df.drop(columns=[TARGET] + DROP_COLS)
CATEGORICAL_COLS = [c for c in X.columns if c not in NUMERIC_COLS]

# Every categorical value is already a string bucket (e.g. "more than 69000",
# "2 to 4"), so one-hot encode them; unseen values at scoring time map to zeros.
encoder = OneHotEncoder(sparse_output=False, handle_unknown="ignore")
encoded = encoder.fit_transform(X[CATEGORICAL_COLS])
all_feature_names = NUMERIC_COLS + list(encoder.get_feature_names_out(CATEGORICAL_COLS))
X_processed = np.hstack([X[NUMERIC_COLS].to_numpy(dtype=float), encoded])
print(f"Features after encoding: {len(all_feature_names)}")

# No scale_pos_weight: unweighted logloss keeps scores close to real
# probabilities, so "18%" means roughly that, against a ~6% base rate.
model = XGBClassifier(
    n_estimators=300,
    max_depth=4,
    learning_rate=0.05,
    subsample=0.8,
    colsample_bytree=0.8,
    min_child_weight=3,
    eval_metric="logloss",
    random_state=42,
    n_jobs=4,
)

cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=0)
oof = cross_val_predict(model, X_processed, y, cv=cv, method="predict_proba")[:, 1]

roc_auc = roc_auc_score(y, oof)
pr_auc = average_precision_score(y, oof)

# Risk bands from the out-of-fold score distribution: High = top 10% of claims,
# Medium = next 20%. Reviewers open the queue from the top, so this is about
# where review time goes, not about a fraud probability cut-off.
high_min = float(np.quantile(oof, 0.90))
medium_min = float(np.quantile(oof, 0.70))
top10 = oof >= high_min
top30 = oof >= medium_min
metrics = {
    "rows": int(len(df)),
    "fraud_rate": round(float(y.mean()), 4),
    "cv_roc_auc": round(float(roc_auc), 4),
    "cv_pr_auc": round(float(pr_auc), 4),
    "top10_precision": round(float(y[top10].mean()), 4),
    "top10_share_of_fraud_caught": round(float(y[top10].sum() / y.sum()), 4),
    "top30_share_of_fraud_caught": round(float(y[top30].sum() / y.sum()), 4),
    "high_min": round(high_min, 4),
    "medium_min": round(medium_min, 4),
}
print(json.dumps(metrics, indent=2))

model.fit(X_processed, y)

explainer = shap.TreeExplainer(model)
sample = X_processed[np.random.RandomState(0).choice(len(y), 2000, replace=False)]
mean_abs_shap = np.abs(explainer.shap_values(sample)).mean(axis=0)
(
    pd.DataFrame({"feature": all_feature_names, "mean_abs_shap": mean_abs_shap})
    .sort_values("mean_abs_shap", ascending=False)
    .to_csv(os.path.join(MODEL_DIR, "feature_importances.csv"), index=False)
)

joblib.dump(model, os.path.join(MODEL_DIR, "xgb_model.joblib"))
joblib.dump(encoder, os.path.join(MODEL_DIR, "onehot_encoder.joblib"))
joblib.dump(explainer, os.path.join(MODEL_DIR, "shap_explainer.joblib"))
with open(os.path.join(MODEL_DIR, "column_metadata.json"), "w") as f:
    json.dump(
        {
            "numeric_cols": NUMERIC_COLS,
            "categorical_cols": CATEGORICAL_COLS,
            "all_feature_names": all_feature_names,
            "category_values": {c: list(map(str, v)) for c, v in zip(CATEGORICAL_COLS, encoder.categories_)},
            "metrics": metrics,
        },
        f,
        indent=2,
    )
print(f"Artefacts saved to {MODEL_DIR}/")
