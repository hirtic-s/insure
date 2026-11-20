async function json(res) {
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.error || res.statusText || `Request failed (${res.status})`);
  return body;
}

export const getClaims = (batchId) =>
  fetch(`/api/claims?limit=200${batchId ? `&batch=${batchId}` : ""}`).then(json);
export const getScores = () => fetch("/api/claims/scores").then(json);
export const getSummary = () => fetch("/api/claims/summary").then(json);
export const getBatch = (id) => fetch(`/api/batches/${id}`).then(json);

export function uploadBatch(file) {
  const form = new FormData();
  form.append("file", file);
  return fetch("/api/batches", { method: "POST", body: form }).then(json);
}

export function submitManual(claims) {
  return fetch("/api/batches/manual", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ claims }),
  }).then(json);
}
