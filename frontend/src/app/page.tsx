export default function Home() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center px-4">
      <div className="max-w-md w-full text-center space-y-6">
        {/* Logo / identité */}
        <div className="space-y-2">
          <h1 className="text-4xl font-bold text-realis-700 dark:text-realis-300 tracking-tight">
            Realis
          </h1>
          <p className="text-sm font-medium text-realis-500 dark:text-realis-400 uppercase tracking-widest">
            Certification du réel
          </p>
        </div>

        {/* Valeur */}
        <p className="text-gray-600 dark:text-gray-300 leading-relaxed">
          Sceller une capture au moment où elle est faite.
          Hash SHA-256 + horodatage RFC&nbsp;3161.{" "}
          <span className="font-medium text-gray-800 dark:text-gray-100">
            Preuve d&apos;intégrité et d&apos;antériorité.
          </span>
        </p>

        {/* Avertissement légal */}
        <p className="text-xs text-gray-400 dark:text-gray-500 border border-gray-200 dark:border-gray-700 rounded-lg p-3">
          Le certificat Realis constitue une preuve d&apos;intégrité et
          d&apos;antériorité (hash cryptographique + horodatage RFC&nbsp;3161).
          Il ne constitue pas un acte authentique au sens juridique.
        </p>

        {/* Actions */}
        <div className="flex flex-col gap-3">
          <a
            href="/nouveau"
            className="w-full py-3 px-6 bg-realis-600 hover:bg-realis-700 text-white font-medium rounded-xl transition-colors"
          >
            Nouvel état des lieux
          </a>
          <a
            href="/verifier"
            className="w-full py-3 px-6 border border-realis-200 dark:border-realis-700 text-realis-600 dark:text-realis-400 hover:bg-realis-50 dark:hover:bg-realis-900/20 font-medium rounded-xl transition-colors"
          >
            Vérifier un fichier
          </a>
        </div>

        {/* Statut API (dev uniquement) */}
        <ApiStatus />
      </div>
    </main>
  );
}

async function ApiStatus() {
  const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

  try {
    const res = await fetch(`${apiUrl}/api/health`, {
      next: { revalidate: 30 },
    });
    const data = (await res.json()) as { status: string; timestamp: string };
    return (
      <p className="text-xs text-green-600 dark:text-green-400">
        Backend : {data.status} · {data.timestamp}
      </p>
    );
  } catch {
    return (
      <p className="text-xs text-red-400 dark:text-red-500">
        Backend inaccessible : vérifiez docker-compose
      </p>
    );
  }
}
