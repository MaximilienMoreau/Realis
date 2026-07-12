"use client";

import { useState, useRef, useEffect } from "react";
import { verifyFile, type VerificationResponse, type Verdict } from "@/lib/api";

export default function VerifierPage() {
  const [file, setFile]         = useState<File | null>(null);
  const [recordId, setRecordId] = useState("");
  const [result, setResult]     = useState<VerificationResponse | null>(null);
  const [loading, setLoading]   = useState(false);
  const [error, setError]       = useState<string | null>(null);
  const inputRef                = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const id = params.get("recordId");
    if (id) setRecordId(id);
  }, []);

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const dropped = e.dataTransfer.files[0];
    if (dropped) setFile(dropped);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) return;

    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const res = await verifyFile(file, recordId || undefined);
      setResult(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Erreur inattendue");
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="min-h-screen bg-gray-50 dark:bg-gray-900 py-10 px-4">
      <div className="max-w-xl mx-auto space-y-6">

        <div className="text-center space-y-1">
          <a href="/" className="text-sm text-realis-500 dark:text-realis-400 hover:underline">← Realis</a>
          <h1 className="text-2xl font-bold text-realis-700 dark:text-realis-300">Vérifier un fichier</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            Re-déposez le fichier original pour vérifier son intégrité et son horodatage.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-100 dark:border-gray-700 p-6 space-y-5">

          <div
            onClick={() => inputRef.current?.click()}
            onDrop={handleDrop}
            onDragOver={(e) => e.preventDefault()}
            className="border-2 border-dashed border-realis-200 dark:border-realis-700 rounded-xl p-8 text-center cursor-pointer
                       hover:border-realis-400 dark:hover:border-realis-500 hover:bg-realis-50 dark:hover:bg-realis-900/20 transition-colors"
          >
            <input
              ref={inputRef}
              type="file"
              className="hidden"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
            {file ? (
              <div className="space-y-1">
                <p className="font-medium text-realis-700 dark:text-realis-300 truncate">{file.name}</p>
                <p className="text-xs text-gray-400 dark:text-gray-500">{formatSize(file.size)}</p>
              </div>
            ) : (
              <div className="space-y-2">
                <p className="text-gray-400 dark:text-gray-500 text-sm">Glissez un fichier ici ou cliquez pour parcourir</p>
                <p className="text-xs text-gray-300 dark:text-gray-600">Tout format accepté</p>
              </div>
            )}
          </div>

          <div className="space-y-1">
            <label className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">
              Identifiant de l&apos;enregistrement <span className="text-gray-300 dark:text-gray-600">(optionnel)</span>
            </label>
            <input
              type="text"
              value={recordId}
              onChange={(e) => setRecordId(e.target.value)}
              placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
              className="w-full px-3 py-2 border border-gray-200 dark:border-gray-600 rounded-lg text-sm font-mono
                         bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100
                         focus:outline-none focus:ring-2 focus:ring-realis-300 dark:focus:ring-realis-700
                         placeholder:text-gray-300 dark:placeholder:text-gray-500"
            />
            <p className="text-xs text-gray-400 dark:text-gray-500">
              Fournir l&apos;ID permet d&apos;obtenir un verdict <strong>ALTÉRÉ</strong> si le fichier a été modifié.
            </p>
          </div>

          <button
            type="submit"
            disabled={!file || loading}
            className="w-full py-3 bg-realis-600 hover:bg-realis-700 disabled:opacity-40
                       text-white font-medium rounded-xl transition-colors"
          >
            {loading ? "Vérification en cours…" : "Vérifier"}
          </button>
        </form>

        {error && (
          <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-300 rounded-xl p-4 text-sm">
            {error}
          </div>
        )}

        {result && <VerificationResult result={result} />}

        <p className="text-xs text-gray-400 dark:text-gray-500 text-center leading-relaxed">
          Ce service constitue une preuve d&apos;intégrité et d&apos;antériorité
          (hash SHA-256 + horodatage RFC&nbsp;3161). Il ne constitue pas un acte
          authentique au sens juridique.
        </p>
      </div>
    </main>
  );
}

// ── Composant résultat ────────────────────────────────────────────────────────

function VerificationResult({ result }: { result: VerificationResponse }) {
  return (
    <div className="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-100 dark:border-gray-700 p-6 space-y-5">
      <VerdictBadge verdict={result.verdict} />

      {result.record?.deleted && (
        <div className="bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 text-amber-700 dark:text-amber-300 rounded-xl p-3 text-sm text-center">
          {result.record.warning ?? "Cet enregistrement a été supprimé. La preuve n'est plus vérifiable."}
        </div>
      )}

      <CheckBlock
        title="(a) Intégrité du fichier"
        passed={result.integrityCheck.passed}
        message={result.integrityCheck.message}
      />

      <div className="space-y-1">
        <p className="text-xs font-medium text-gray-400 dark:text-gray-500 uppercase tracking-wide">Hash SHA-256 du fichier soumis</p>
        <p className="font-mono text-xs break-all text-gray-700 dark:text-gray-300 bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3">
          {result.uploadedSha256}
        </p>
      </div>

      {result.tsaCheck ? (
        <CheckBlock
          title="(b) Horodatage RFC 3161"
          passed={result.tsaCheck.valid || result.tsaCheck.isNoOp}
          warning={result.tsaCheck.isNoOp}
          message={result.tsaCheck.message}
        />
      ) : null}

      {result.record && <RecordDetails record={result.record} />}
    </div>
  );
}

function VerdictBadge({ verdict }: { verdict: Verdict }) {
  const styles: Record<Verdict, string> = {
    AUTHENTIQUE: "bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-800 text-green-800 dark:text-green-300",
    ALTERE:      "bg-red-50  dark:bg-red-900/20  border-red-200  dark:border-red-800  text-red-800  dark:text-red-300",
    INCONNU:     "bg-gray-50 dark:bg-gray-700    border-gray-200 dark:border-gray-600  text-gray-600 dark:text-gray-300",
  };
  const labels: Record<Verdict, string> = {
    AUTHENTIQUE: "✓ AUTHENTIQUE",
    ALTERE:      "✗ ALTÉRÉ",
    INCONNU:     "? INCONNU",
  };

  return (
    <div className={`border-2 rounded-xl px-5 py-4 text-center ${styles[verdict]}`}>
      <p className="text-xl font-bold tracking-wide">{labels[verdict]}</p>
    </div>
  );
}

function CheckBlock({
  title, passed, message, warning = false,
}: {
  title: string;
  passed: boolean;
  message: string;
  warning?: boolean;
}) {
  const color = warning
    ? "text-amber-600 dark:text-amber-400"
    : passed
      ? "text-green-600 dark:text-green-400"
      : "text-red-600 dark:text-red-400";
  const icon = warning ? "⚠" : passed ? "✓" : "✗";

  return (
    <div className="space-y-1">
      <p className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">{title}</p>
      <p className={`text-sm font-medium ${color}`}>
        {icon} {message}
      </p>
    </div>
  );
}

function RecordDetails({ record }: { record: NonNullable<VerificationResponse["record"]> }) {
  const fmt = (iso: string) =>
    new Date(iso).toLocaleString("fr-FR", { timeZone: "UTC" }) + " UTC";

  return (
    <div className="border-t border-gray-100 dark:border-gray-700 pt-4 space-y-2">
      <p className="text-xs font-medium text-gray-400 dark:text-gray-500 uppercase tracking-wide">Enregistrement scellé</p>
      <table className="w-full text-xs">
        <tbody className="divide-y divide-gray-50 dark:divide-gray-700">
          {[
            ["Référence",   record.id],
            ["Fichier",     record.fileName],
            ["Taille",      formatSize(record.fileSizeBytes)],
            ["Format",      record.mimeType],
            ["Scellé le",   fmt(record.sealedAt)],
            ["TSA",         record.tsaTimestamp ? fmt(record.tsaTimestamp) : "Non disponible"],
          ].map(([label, value]) => (
            <tr key={label}>
              <td className="py-1.5 pr-3 text-gray-400 dark:text-gray-500 font-medium whitespace-nowrap">{label}</td>
              <td className="py-1.5 font-mono text-gray-700 dark:text-gray-300 break-all">{value}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ── Utilitaires ───────────────────────────────────────────────────────────────

function formatSize(bytes: number): string {
  if (bytes < 1024)              return `${bytes} o`;
  if (bytes < 1024 * 1024)      return `${(bytes / 1024).toFixed(1)} Ko`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
}
