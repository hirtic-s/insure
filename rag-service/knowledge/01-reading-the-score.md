# Reading the fraud risk score

## What the score is
The score is an XGBoost model's estimated probability that a claim is fraudulent. About 6% of the training claims were labelled fraud, so a score near 6% is a typical claim and a score of 0.9% is far below typical.

## What the score is not
The score ranks claims for review. It never decides a claim. Most claims in the High band are still legitimate, and fraud is found in other bands too. Never deny, delay or reduce a claim on the score alone.

## Bands
Bands come from the score distribution: High is roughly the top 10% of claims, Medium the next 20%, and Low the rest. Low means the model ranks the claim below most others. It does not mean the claim is verified or safe.

## How well the model ranks
Cross-validated ROC-AUC is 0.83. The top 10% of claims by score hold about 36% of the fraud, with precision around 21%. That is useful for ordering a queue and too weak to treat any single score as evidence.
