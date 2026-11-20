import { useCallback, useEffect, useRef, useState } from "react";
import { getBatch, uploadBatch } from "./api.js";
import { AlertIcon, CheckIcon, FileIcon, UploadIcon } from "./icons.jsx";
import { duration } from "./format.js";

const MAX_BYTES = 25 * 1024 * 1024;

// Progress is derived from polling; throughput uses a sliding window of recent samples.
function useBatchProgress(batchId, onTick) {
  const [batch, setBatch] = useState(null);
  const [rate, setRate] = useState(null);
  const samples = useRef([]);

  useEffect(() => {
    if (!batchId) return;
    samples.current = [];
    setBatch(null);
    setRate(null);
    let stopped = false;
    let timer;
    const tick = async () => {
      try {
        const b = await getBatch(batchId);
        if (stopped) return;
        setBatch(b);
        const done = b.scored + b.unscored;
        const now = Date.now();
        samples.current = [...samples.current, { t: now, done }].filter((s) => now - s.t <= 6000);
        const first = samples.current[0];
        if (samples.current.length > 1 && now > first.t) {
          setRate(((done - first.done) / (now - first.t)) * 1000);
        }
        onTick?.();
        if (done < b.accepted) timer = setTimeout(tick, 1000);
      } catch {
        if (!stopped) timer = setTimeout(tick, 3000);
      }
    };
    tick();
    return () => {
      stopped = true;
      clearTimeout(timer);
    };
  }, [batchId]); // eslint-disable-line react-hooks/exhaustive-deps

  return { batch, rate };
}

function Dropzone({ onFile, error, compact }) {
  const [over, setOver] = useState(false);
  const input = useRef(null);

  const take = (file) => file && onFile(file);

  return (
    <div
      className={`dropzone${over ? " over" : ""}${compact ? " compact" : ""}`}
      onDragOver={(e) => { e.preventDefault(); setOver(true); }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => { e.preventDefault(); setOver(false); take(e.dataTransfer.files[0]); }}
    >
      <input ref={input} type="file" accept=".csv,text/csv" hidden onChange={(e) => { take(e.target.files[0]); e.target.value = ""; }} />
      <UploadIcon className="dz-icon" />
      <div className="dz-copy">
        <p className="dz-title">{compact ? "Add another claims file" : "Drop a claims CSV to score it"}</p>
        {!compact && (
          <p className="dz-hint">
            Each row becomes a claim and is scored in the background. Needs columns{" "}
            <code>age_of_driver</code>, <code>gender</code>, <code>accident_site</code>, <code>claim_date</code>, <code>total_claim</code>.
          </p>
        )}
      </div>
      <div className="dz-actions">
        <button type="button" className="btn primary" onClick={() => input.current.click()}>Choose file</button>
        {!compact && <a className="btn ghost" href="/sample-claims.csv" download>Download 200-claim sample</a>}
      </div>
      {error && (
        <p className="dz-error" role="alert"><AlertIcon /> {error}</p>
      )}
    </div>
  );
}

function Progress({ batch, rate, onReset }) {
  const total = batch.accepted;
  const done = batch.scored + batch.unscored;
  const finished = done >= total;
  const fraction = total ? done / total : 0;
  const remaining = rate > 0 ? ((total - done) / rate) * 1000 : null;

  return (
    <div className="progress" role="status" aria-live="polite">
      <div className="pg-head">
        <FileIcon className="pg-file" />
        <div className="pg-name">
          <strong>{batch.filename}</strong>
          <span className="muted">
            {total.toLocaleString()} claims accepted
            {batch.rejected > 0 && <> · {batch.rejected.toLocaleString()} rejected</>}
          </span>
        </div>
        {finished && <button type="button" className="btn ghost" onClick={onReset}>Upload another</button>}
      </div>

      <div className="bar" aria-hidden="true"><i style={{ width: `${fraction * 100}%` }} /></div>

      <div className="pg-meta">
        {finished ? (
          <span className="done"><CheckIcon /> All {total.toLocaleString()} claims processed</span>
        ) : (
          <span>
            <strong className="num">{done.toLocaleString()}</strong> of <span className="num">{total.toLocaleString()}</span> scored
          </span>
        )}
        {!finished && rate != null && rate > 0 && (
          <span className="muted num">{rate.toFixed(1)} claims/s{remaining != null && ` · about ${duration(Math.ceil(remaining / 1000) * 1000)} left`}</span>
        )}
        {batch.unscored > 0 && <span className="warn num">{batch.unscored} awaiting retry (scoring service unavailable)</span>}
      </div>

      {batch.rejected > 0 && batch.firstRejection && (
        <p className="pg-reject"><AlertIcon /> {batch.firstRejection}</p>
      )}
    </div>
  );
}

export default function Upload({ onActivity }) {
  const [batchId, setBatchId] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState(null);
  const { batch, rate } = useBatchProgress(batchId, onActivity);

  const handleFile = useCallback(async (file) => {
    setError(null);
    if (!/\.csv$/i.test(file.name)) return setError("That isn't a .csv file. Export your claims as CSV and try again.");
    if (file.size > MAX_BYTES) return setError("That file is over 25 MB. Split it into smaller files.");
    setUploading(true);
    try {
      const b = await uploadBatch(file);
      setBatchId(b.id);
      onActivity?.();
    } catch (e) {
      setError(e.message === "Failed to fetch" ? "Can't reach the claims service. Check that the backend is running." : e.message);
    } finally {
      setUploading(false);
    }
  }, [onActivity]);

  if (uploading) {
    return <div className="progress" role="status"><div className="pg-head"><FileIcon className="pg-file" /><strong>Uploading…</strong></div><div className="bar indeterminate" aria-hidden="true"><i /></div></div>;
  }
  if (batchId && batch) {
    return <Progress batch={batch} rate={rate} onReset={() => setBatchId(null)} />;
  }
  if (batchId) {
    return <div className="progress" role="status"><div className="pg-head"><FileIcon className="pg-file" /><strong>Queued…</strong></div><div className="bar indeterminate" aria-hidden="true"><i /></div></div>;
  }
  return <Dropzone onFile={handleFile} error={error} compact={false} />;
}
