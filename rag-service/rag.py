"""RAG core: chunking, Gemini embeddings, pgvector storage and retrieval."""

import os
from pathlib import Path

import psycopg
from google import genai
from google.genai import types
from pgvector.psycopg import register_vector

EMBED_MODEL = "gemini-embedding-001"
EMBED_DIM = 768
GEN_MODEL = os.environ.get("GEMINI_MODEL", "gemini-2.5-flash")
DB_URL = os.environ.get(
    "DATABASE_URL", "postgresql://insurance:insurance@localhost:5432/insurance"
)
KNOWLEDGE_DIR = Path(__file__).parent / "knowledge"

_client: genai.Client | None = None


def client() -> genai.Client:
    global _client
    if _client is None:
        # Reads GEMINI_API_KEY from the environment.
        _client = genai.Client()
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
        config=types.EmbedContentConfig(
            task_type=task_type, output_dimensionality=EMBED_DIM
        ),
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


def load_chunks() -> list[tuple[str, str, str]]:
    """One chunk per bullet-group section: (source, title, content)."""
    chunks = []
    for path in sorted(KNOWLEDGE_DIR.glob("*.md")):
        text = path.read_text().strip()
        title = text.splitlines()[0].lstrip("# ").strip()
        chunks.append((path.name, title, text))
    return chunks


def ingest(conn: psycopg.Connection) -> int:
    init_schema(conn)
    chunks = load_chunks()
    vectors = embed([c[2] for c in chunks], "RETRIEVAL_DOCUMENT")
    conn.execute("TRUNCATE knowledge_chunks")
    with conn.cursor() as cur:
        cur.executemany(
            "INSERT INTO knowledge_chunks (source, title, content, embedding) "
            "VALUES (%s, %s, %s, %s)",
            [(s, t, c, v) for (s, t, c), v in zip(chunks, vectors)],
        )
    return len(chunks)


def retrieve(conn: psycopg.Connection, query: str, k: int = 4) -> list[dict]:
    qvec = embed([query], "RETRIEVAL_QUERY")[0]
    rows = conn.execute(
        "SELECT source, title, content, 1 - (embedding <=> %s::vector) AS similarity "
        "FROM knowledge_chunks ORDER BY embedding <=> %s::vector LIMIT %s",
        (qvec, qvec, k),
    ).fetchall()
    return [
        {"source": r[0], "title": r[1], "content": r[2], "similarity": round(float(r[3]), 4)}
        for r in rows
    ]
