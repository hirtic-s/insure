# InSure! RAG Briefing Service

Drafts a cited investigator briefing for a scored claim. The guidance in `knowledge/` is split by
`##` section, embedded with Gemini (`gemini-embedding-001`, 768-d) into a pgvector table, and
retrieved per claim. Gemini writes the briefing from the claim record plus the retrieved sections,
citing them as `[S1]`, `[S2]` and the claim record as `[C]`. Briefings are cached in Postgres per
claim and input, so reopening a claim costs no API call.

`knowledge/` is a starter playbook. Replace it with your own special-investigations guidance;
the service re-embeds it automatically when the files change.

```bash
cd rag-service
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
export GEMINI_API_KEY=...        # free key from https://aistudio.google.com/apikey
uvicorn app:app --port 8001
```

Needs Postgres with the pgvector extension (`pgvector/pgvector:pg17` in the compose files).
`DATABASE_URL` defaults to the compose Postgres, `GEMINI_MODEL` to `gemini-2.5-flash`.
`POST /ingest` forces a re-embed; `POST /briefing?refresh=true` skips the cache.
