"use client";

import { useEffect, useState } from "react";
import { listMySeals, AuthExpiredError, type SealResponse } from "@/lib/api";
import { isAuthenticated } from "@/lib/auth";

export default function MesPreuvesPage() {
  const [records, setRecords] = useState<SealResponse[] | null>(null);
  const [error, setError]     = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!isAuthenticated()) {
      setError("Vous devez être connecté pour voir vos preuves.");
      setLoading(false);
      return;
    }
    listMySeals()
      .then(setRecords)
      .catch((err) => setError(err instanceof AuthExpiredError ? err.message : "Erreur lors du chargement"))
      .finally(() => setLoading(false));
  }, []);

  return (
    <main className="min-h-screen bg-gray-50 dark:bg-gray-900 py-10 px-4">
      <div className="max-w-xl mx-auto space-y-6">

        <div className="text-center space-y-1">
          <a href="/" className="text-sm text-realis-500 dark:text-realis-400 hover:underline">← Realis</a>
          <h1 className="text-2xl font-bold text-realis-700 dark:text-realis-300">Mes preuves</h1>
        </div>

        {loading && (
          <div className="bg-white dark:bg-gray-800 rounded-2xl border border-gray-100 dark:border-gray-700 shadow-sm p-10 flex justify-center">
            <div className="w-8 h-8 rounded-full border-4 border-realis-200 dark:border-realis-800 border-t-realis-600 animate-spin" />
          </div>
        )}

        {error && (
          <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-300 rounded-2xl p-6 text-center text-sm space-y-3">
            <p>{error}</p>
            <a href="/nouveau" className="inline-block text-realis-600 dark:text-realis-400 hover:underline">
              Se connecter →
            </a>
          </div>
        )}

        {records && records.length === 0 && (
          <div className="bg-white dark:bg-gray-800 rounded-2xl border border-gray-100 dark:border-gray-700 shadow-sm p-8 text-center text-sm text-gray-400 dark:text-gray-500">
            Aucune preuve scellée pour l&apos;instant.
            <br />
            <a href="/nouveau" className="text-realis-600 dark:text-realis-400 hover:underline">
              Créer un état des lieux →
            </a>
          </div>
        )}

        {records && records.length > 0 && (
          <ul className="space-y-3">
            {records.map((r) => (
              <li key={r.id}>
                <a
                  href={`/certificat/${r.id}`}
                  className="block bg-white dark:bg-gray-800 rounded-2xl border border-gray-100 dark:border-gray-700
                             shadow-sm p-4 hover:border-realis-300 dark:hover:border-realis-600 transition-colors"
                >
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="font-medium text-sm text-realis-700 dark:text-realis-300 truncate">{r.fileName}</p>
                      <p className="text-xs text-gray-400 dark:text-gray-500">{fmt(r.sealedAt)}</p>
                    </div>
                    <div className={`w-2.5 h-2.5 rounded-full flex-shrink-0 ${r.tsaActive ? "bg-green-500" : "bg-amber-400"}`} />
                  </div>
                  <p className="mt-2 font-mono text-[11px] text-gray-400 dark:text-gray-500 break-all">{r.sha256Hex}</p>
                </a>
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  );
}

function fmt(iso: string): string {
  return new Date(iso).toLocaleString("fr-FR", { timeZone: "UTC" }) + " UTC";
}
