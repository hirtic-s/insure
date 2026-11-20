async function json(res) {
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.error || res.statusText || `Request failed (${res.status})`);
  return body;
}

export const getClaims = () => fetch("/api/claims?limit=200").then(json);
export const getSummary = () => fetch("/api/claims/summary").then(json);
export const getBatch = (id) => fetch(`/api/batches/${id}`).then(json);
export const requestBriefing = (id) => fetch(`/api/claims/${id}/briefing`, { method: "POST" }).then(json);

export function uploadBatch(file) {
  const form = new FormData();
  form.append("file", file);
  return fetch("/api/batches", { method: "POST", body: form }).then(json);
}
