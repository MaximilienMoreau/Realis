"use client";

import { useEffect, useState } from "react";
import { getSealRecord, certificateUrl, type SealResponse } from "@/lib/api";

export default function CertificatPage({ params }: { params: { id: string } }) {
  const [record, setRecord]   = useState<SealResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState<string | null>(null);

  useEffect(() => {
    getSealRecord(params.id)
      .then(setRecord)
      .catch((err) => setError(err instanceof Error ? err.message : "Enregistrement introuvable"))
      .finally(() => setLoading(false));
  }, [params.id]);

  return (
    <main className="min-h-screen bg-gray-50 dark:bg-gray-900 py-10 px-4">
      <div className="max-w-xl mx-auto space-y-6">

        <div className="text-center space-y-1">
          <a href="/" className="text-sm text-realis-500 dark:text-realis-400 hover:underline">← Realis</a>
          <h1 className="text-2xl font-bold text-realis-700 dark:text-realis-300">Preuve d&apos;intégrité</h1>
        </div>

        {loading && (
          <div className="bg-white dark:bg-gray-800 rounded-2xl border border-gray-100 dark:border-gray-700 shadow-sm p-10 flex justify-center">
            <div className="w-8 h-8 rounded-full border-4 border-realis-200 dark:border-realis-800 border-t-realis-600 animate-spin" />
          </div>
        )}

        {error && (
          <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-300 rounded-2xl p-6 text-center text-sm">
            {error}
          </div>
        )}

        {record && (
          <>
            {record.deleted && (
              <div className="bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 text-amber-700 dark:text-amber-300 rounded-xl p-3 text-sm text-center">
                {record.warning ?? "Cet enregistrement a été supprimé. La preuve n'est plus vérifiable."}
              </div>
            )}

            <div className="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-100 dark:border-gray-700 p-6 space-y-5">

              <div className="space-y-1">
                <p className="text-xs text-gray-400 dark:text-gray-500 uppercase tracking-wide font-medium">Référence</p>
                <p className="font-mono text-sm text-realis-700 dark:text-realis-300 break-all">{record.id}</p>
              </div>

              <hr className="border-gray-100 dark:border-gray-700" />

              <div className="flex items-center gap-3">
                <div className={`w-2.5 h-2.5 rounded-full flex-shrink-0 ${record.tsaActive ? "bg-green-500" : "bg-amber-400"}`} />
                <div>
                  <p className="text-xs font-medium text-gray-700 dark:text-gray-300">
                    Horodatage RFC 3161 — {record.tsaActive ? "ACTIF" : "NON ACTIF (développement)"}
                  </p>
                  {record.tsaActive && record.tsaTimestamp && (
                    <p className="text-xs text-gray-400 dark:text-gray-500">{fmt(record.tsaTimestamp)}</p>
                  )}
                </div>
              </div>

              <hr className="border-gray-100 dark:border-gray-700" />

              <table className="w-full text-xs">
                <tbody className="divide-y divide-gray-50 dark:divide-gray-700">
                  {([
                    ["Scellé le",   fmt(record.sealedAt)],
                    ["Fichier",     record.fileName],
                    ["Taille",      formatSize(record.fileSizeBytes)],
                    ["Format",      record.mimeType],
                    ...(record.geolocLat != null && record.geolocLng != null
                      ? [["Géolocalisation", `${record.geolocLat.toFixed(6)}, ${record.geolocLng.toFixed(6)}`]]
                      : []),
                  ] as [string, string][]).map(([label, value]) => (
                    <tr key={label}>
                      <td className="py-1.5 pr-3 text-gray-400 dark:text-gray-500 font-medium whitespace-nowrap">{label}</td>
                      <td className="py-1.5 text-gray-700 dark:text-gray-300 break-all">{value}</td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <hr className="border-gray-100 dark:border-gray-700" />

              <div className="space-y-1">
                <p className="text-xs font-medium text-gray-400 dark:text-gray-500 uppercase tracking-wide">Hash SHA-256</p>
                <p className="font-mono text-xs break-all text-gray-700 dark:text-gray-300 bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3">
                  {record.sha256Hex}
                </p>
              </div>

              <div className="flex flex-col gap-3 pt-1">
                <a
                  href={certificateUrl(record.id)}
                  download={`realis-certificat-${record.id}.pdf`}
                  className="block w-full py-3 bg-realis-600 hover:bg-realis-700 text-white
                             font-medium rounded-xl transition-colors text-center"
                >
                  Télécharger le certificat PDF
                </a>
                <a
                  href={`/verifier?recordId=${record.id}`}
                  className="block w-full py-2.5 border border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700
                             font-medium rounded-xl transition-colors text-center text-sm"
                >
                  Vérifier ce fichier
                </a>
              </div>
            </div>

            <div className="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-100 dark:border-gray-700 p-5 space-y-3">
              <p className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">
                Vérification indépendante
              </p>
              <p className="text-xs text-gray-500 dark:text-gray-400 leading-relaxed">
                Le token TSA (.tsr) peut être vérifié hors-ligne avec OpenSSL :
              </p>
              <pre className="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3 text-xs text-gray-700 dark:text-gray-300 overflow-x-auto whitespace-pre-wrap">
                {`openssl ts -verify \\
  -in realis-tsa-${record.id}.tsr \\
  -digest ${record.sha256Hex.slice(0, 16)}… \\
  -CAfile freetsa.crt`}
              </pre>
              <a
                href={`/api/verify/${record.id}/tsa`}
                download={`realis-tsa-${record.id}.tsr`}
                className="text-xs text-realis-500 dark:text-realis-400 hover:underline"
              >
                Télécharger le token TSA brut (.tsr) →
              </a>
            </div>

            <p className="text-xs text-gray-400 dark:text-gray-500 text-center leading-relaxed">
              Ce certificat constitue une preuve d&apos;intégrité et d&apos;antériorité
              (hash SHA-256 + horodatage RFC&nbsp;3161). Il ne constitue pas un acte
              authentique au sens juridique.
            </p>
          </>
        )}
      </div>
    </main>
  );
}

// ── Utilitaires ───────────────────────────────────────────────────────────────

function fmt(iso: string): string {
  return new Date(iso).toLocaleString("fr-FR", { timeZone: "UTC" }) + " UTC";
}

function formatSize(bytes: number): string {
  if (bytes < 1024)         return `${bytes} o`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} Ko`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
}
