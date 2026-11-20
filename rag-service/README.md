# InSure! RAG Briefing Service

Retrieval-augmented assistant that drafts cited investigator briefings.
Guidance in `knowledge/` is embedded with Gemini (`gemini-embedding-001`, 768-d)
into a pgvector table; `POST /briefing` retrieves the top matches for a claim and
asks Gemini to write a briefing citing them as `[S1]`, `[S2]`, …

```bash
cd rag-service
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
export GEMINI_API_KEY=...        # required
uvicorn app:app --port 8001
curl -X POST localhost:8001/ingest   # embed knowledge/ into pgvector (run once, and after edits)
```

`DATABASE_URL` defaults to the docker-compose Postgres. `GEMINI_MODEL` defaults to `gemini-2.5-flash`.
