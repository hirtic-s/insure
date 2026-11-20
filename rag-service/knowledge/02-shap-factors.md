# Reading the SHAP factors

## Direction and size
Each factor is a SHAP contribution to this claim's score, in log-odds. "Increases risk" pushes the score up from the typical claim and "decreases risk" pushes it down. The number in brackets is the size. One large factor can outweigh several small ones pointing the other way, so judge factors by size, not by how many point each way.

## Policy factors can dominate
In the training data, liability-only policies were rarely labelled fraud, so the model learned that a Liability base policy lowers risk strongly. A Low score on such a claim can come almost entirely from the policy type. Say so in the briefing, and check the claim's own facts instead of relying on that factor.

## Factors that are not evidence
Driver age, sex, marital status and the month a claim was filed can show up as factors because of patterns in the training data. They are not signs of wrongdoing. Never base a decision or a recommended action on them.
