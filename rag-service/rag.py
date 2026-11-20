"""RAG core: chunking, Gemini embeddings, pgvector storage and retrieval, briefing cache."""

import hashlib
import json
import os
import re
from pathlib import Path

import psycopg
from google import genai
from google.genai import types
from pgvector.psycopg import register_vector

EMBED_MODEL = "gemini-embedding-001"
EMBED_DIM = 768
GEN_MODEL = os.environ.get("GEMINI_MODEL", "gemini-2.5-flash")
DB_URL = os.environ.get("DATABASE_URL", "postgresql://insurance:insurance@localhost:5432/insurance")
KNOWLEDGE_DIR = Path(os.environ.get("KNOWLEDGE_DIR", Path(__file__).parent / "knowledge"))

_client: genai.Client | None = None


class NotConfigured(RuntimeError):
    pass


def client() -> genai.Client:
    global _client
    if _client is None:
        key = os.environ.get("GEMINI_API_KEY")
        if not key:
            raise NotConfigured("GEMINI_API_KEY is not set on the RAG service.")
        _client = genai.Client(api_key=key)
    return _client


def connect() -> psycopg.Connection:
    conn = psycopg.connect(DB_URL, autocommit=True)
    conn.execute("CREATE EXTENSION IF NOT EXISTS vector")
    register_vector(conn)
    return conn


def embed(texts: list[str], task_type: str) -> list[list[float]]:
    res = client().models.embed_content(
        model=EMBED_MODEL,
        contents=texts,
        config=types.EmbedContentConfig(task_type=task_type, output_dimensionality=EMBED_DIM),
    )
    return [e.values for e in res.embeddings]


def init_schema(conn: psycopg.Connection) -> None:
    conn.execute(
        f"""
        CREATE TABLE IF NOT EXISTS knowledge_chunks (
            id SERIAL PRIMARY KEY,
            source TEXT NOT NULL,
            title TEXT NOT NULL,
            content TEXT NOT NULL,
            embedding vector({EMBED_DIM}) NOT NULL
        )
        """
    )
    conn.execute(
        "CREATE INDEX IF NOT EXISTS knowledge_chunks_embedding_idx "
        "ON knowledge_chunks USING hnsw (embedding vector_cosine_ops)"
    )
    conn.execute("CREATE TABLE IF NOT EXISTS rag_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS claim_briefings (
            claim_id TEXT NOT NULL,
            input_hash TEXT NOT NULL,
            briefing TEXT NOT NULL,
            citations JSONB NOT NULL,
            model TEXT NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            PRIMARY KEY (claim_id, input_hash)
        )
        """
    )


def load_chunks() -> list[tuple[str, str, str]]:
    """One chunk per '## ' section: (source file, 'Document: Section', text)."""
    chunks = []
    for path in sorted(KNOWLEDGE_DIR.glob("*.md")):
        text = path.read_text().strip()
        doc = text.splitlines()[0].lstrip("# ").strip()
        for part in re.split(r"(?m)^(?=## )", text)[1:]:
            section = part.splitlines()[0].lstrip("# ").strip()
            body = part.split("\n", 1)[1].strip() if "\n" in part else ""
            if body:
                chunks.append((path.name, f"{doc}: {section}", f"{doc}: {section}\n{body}"))
    return chunks


def knowledge_hash() -> str:
    h = hashlib.sha256()
    for path in sorted(KNOWLEDGE_DIR.glob("*.md")):
        h.update(path.name.encode())
        h.update(path.read_bytes())
    return h.hexdigest()[:16]


def ingest(conn: psycopg.Connection, force: bool = False) -> int:
    """Embed the knowledge base. Skipped when it is unchanged since the last run."""
    init_schema(conn)
    digest = knowledge_hash()
    row = conn.execute("SELECT value FROM rag_meta WHERE key = 'knowledge_hash'").fetchone()
    count = conn.execute("SELECT count(*) FROM knowledge_chunks").fetchone()[0]
    if not force and row and row[0] == digest and count:
        return count

    chunks = load_chunks()
    vectors = embed([c[2] for c in chunks], "RETRIEVAL_DOCUMENT")
    with conn.transaction():
        conn.execute("TRUNCATE knowledge_chunks")
        with conn.cursor() as cur:
            cur.executemany(
                "INSERT INTO knowledge_chunks (source, title, content, embedding) VALUES (%s, %s, %s, %s)",
                [(s, t, c, v) for (s, t, c), v in zip(chunks, vectors)],
            )
        conn.execute(
            "INSERT INTO rag_meta (key, value) VALUES ('knowledge_hash', %s) "
            "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value",
            (digest,),
        )
    return len(chunks)


def retrieve(conn: psycopg.Connection, query: str, k: int = 5) -> list[dict]:
    qvec = embed([query], "RETRIEVAL_QUERY")[0]
    rows = conn.execute(
        "SELECT source, title, content, 1 - (embedding <=> %s::vector) AS similarity "
        "FROM knowledge_chunks ORDER BY embedding <=> %s::vector LIMIT %s",
        (qvec, qvec, k),
    ).fetchall()
    return [
        {"source": r[0], "title": r[1], "content": r[2], "similarity": round(float(r[3]), 4)} for r in rows
    ]


def cached_briefing(conn: psycopg.Connection, claim_id: str, input_hash: str) -> dict | None:
    row = conn.execute(
        "SELECT briefing, citations, model FROM claim_briefings WHERE claim_id = %s AND input_hash = %s",
        (claim_id, input_hash),
    ).fetchone()
    return {"briefing": row[0], "citations": row[1], "model": row[2]} if row else None


def store_briefing(conn, claim_id: str, input_hash: str, briefing: str, citations: list[dict]) -> None:
    conn.execute(
        "INSERT INTO claim_briefings (claim_id, input_hash, briefing, citations, model) "
        "VALUES (%s, %s, %s, %s, %s) ON CONFLICT (claim_id, input_hash) DO UPDATE "
        "SET briefing = EXCLUDED.briefing, citations = EXCLUDED.citations, created_at = now()",
        (claim_id, input_hash, briefing, json.dumps(citations), GEN_MODEL),
    )
