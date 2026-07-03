"use client";

import { useState } from "react";
import ConsentForm, { type ConsentData } from "@/components/ConsentForm";
import VideoCapture, { type CaptureMetadata } from "@/components/VideoCapture";
import { registerUser, loginUser, sealCapture, certificateUrl, AuthExpiredError, type SealResponse } from "@/lib/api";
import { saveAuth, getStoredUser, clearAuth, isAuthenticated } from "@/lib/auth";

// ── Types ─────────────────────────────────────────────────────────────────────

type Step = "auth" | "consent" | "capture" | "sealing" | "done";

// ── Composant principal ───────────────────────────────────────────────────────

export default function NouveauPage() {
  const [step, setStep]             = useState<Step>("auth");
  const [consent, setConsent]       = useState<ConsentData | null>(null);
  const [sealResult, setSealResult] = useState<SealResponse | null>(null);
  const [sealError, setSealError]   = useState<string | null>(null);

  const handleAuth = () => { setSealError(null); setStep("consent"); };

  const handleConsent = (data: ConsentData) => {
    setConsent(data);
    setStep("capture");
  };

  const handleCaptured = async (blob: Blob, metadata: CaptureMetadata) => {
    if (!consent) return;
    setStep("sealing");
    setSealError(null);
    try {
      const record = await sealCapture({
        blob,
        mimeType:  metadata.mimeType,
        geolocLat: metadata.geolocLat,
        geolocLng: metadata.geolocLng,
        deviceUa:  metadata.deviceUa,
      });
      setSealResult(record);
      setStep("done");
    } catch (err) {
      if (err instanceof AuthExpiredError) {
        setSealError(err.message);
        setStep("auth");
        return;
      }
      setSealError(err instanceof Error ? err.message : "Erreur inattendue lors du scellement.");
      setStep("capture");
    }
  };

  return (
    <main className="min-h-screen bg-gray-50 dark:bg-gray-900 py-10 px-4">
      <div className="max-w-md mx-auto space-y-6">

        <div className="text-center space-y-1">
          <a href="/" className="text-sm text-realis-500 dark:text-realis-400 hover:underline">← Realis</a>
          <h1 className="text-2xl font-bold text-realis-700 dark:text-realis-300">Nouvel état des lieux</h1>
        </div>

        <StepIndicator current={step} />

        {sealError && (
          <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-300 rounded-xl p-3 text-sm">
            {sealError}
          </div>
        )}

        <div className="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-100 dark:border-gray-700 p-6">
          {step === "auth" && <AuthStep onAuth={handleAuth} />}
          {step === "consent" && <ConsentForm onConsent={handleConsent} />}
          {step === "capture" && consent && (
            <VideoCapture
              geolocConsented={consent.geolocConsented}
              onCaptured={handleCaptured}
            />
          )}
          {step === "sealing" && <SealingStep />}
          {step === "done" && sealResult && <DoneStep record={sealResult} />}
        </div>

        <p className="text-xs text-gray-400 dark:text-gray-500 text-center leading-relaxed">
          Le certificat constitue une preuve d&apos;intégrité et d&apos;antériorité
          (hash SHA-256 + horodatage RFC&nbsp;3161). Il ne constitue pas un acte
          authentique au sens juridique.
        </p>
      </div>
    </main>
  );
}

// ── Indicateur d'étapes ───────────────────────────────────────────────────────

const STEPS: { id: Step; label: string }[] = [
  { id: "auth",    label: "Connexion" },
  { id: "consent", label: "Consentement" },
  { id: "capture", label: "Capture" },
  { id: "sealing", label: "Scellement" },
  { id: "done",    label: "Certificat" },
];

function StepIndicator({ current }: { current: Step }) {
  const currentIndex = STEPS.findIndex((s) => s.id === current);
  return (
    <div className="flex items-center gap-1">
      {STEPS.map((s, i) => {
        const isDone   = i < currentIndex;
        const isActive = i === currentIndex;
        return (
          <div key={s.id} className="flex-1 flex flex-col items-center gap-1">
            <div className={`h-1.5 w-full rounded-full transition-colors ${
              isDone ? "bg-realis-500" : isActive ? "bg-realis-300 dark:bg-realis-600" : "bg-gray-100 dark:bg-gray-700"
            }`} />
            <span className={`text-[10px] font-medium ${
              isActive ? "text-realis-600 dark:text-realis-400" : isDone ? "text-realis-400 dark:text-realis-500" : "text-gray-300 dark:text-gray-600"
            }`}>
              {s.label}
            </span>
          </div>
        );
      })}
    </div>
  );
}

// ── Étape Auth ────────────────────────────────────────────────────────────────

type AuthMode = "login" | "register";

function AuthStep({ onAuth }: { onAuth: () => void }) {
  const [mode, setMode]         = useState<AuthMode>("login");
  const [email, setEmail]       = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading]   = useState(false);
  const [error, setError]       = useState<string | null>(null);

  const stored = getStoredUser();
  if (isAuthenticated() && stored) {
    return (
      <div className="space-y-4">
        <div className="text-center space-y-1">
          <h2 className="text-lg font-semibold text-realis-700 dark:text-realis-300">Connecté</h2>
          <p className="text-sm text-gray-500 dark:text-gray-400">{stored.email}</p>
        </div>
        <button
          type="button"
          onClick={onAuth}
          className="w-full py-3 bg-realis-600 hover:bg-realis-700 text-white font-medium rounded-xl transition-colors"
        >
          Continuer
        </button>
        <a
          href="/mes-preuves"
          className="block w-full py-2.5 text-center text-sm border border-gray-200 dark:border-gray-700
                     text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 rounded-xl transition-colors"
        >
          Voir mes preuves
        </a>
        <button
          type="button"
          onClick={() => { clearAuth(); window.location.reload(); }}
          className="w-full py-2 text-sm text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
        >
          Se déconnecter
        </button>
      </div>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const result = mode === "login"
        ? await loginUser(email.trim(), password)
        : await registerUser(email.trim(), password);
      saveAuth(result.token, result.userId, result.email);
      onAuth();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Erreur d'authentification");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-5">
      <div className="text-center space-y-1">
        <h2 className="text-lg font-semibold text-realis-700 dark:text-realis-300">Identification</h2>
        <p className="text-sm text-gray-500 dark:text-gray-400">Vos captures sont liées à votre compte.</p>
      </div>

      <div className="flex rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden text-sm font-medium">
        {(["login", "register"] as AuthMode[]).map((m) => (
          <button
            key={m}
            type="button"
            onClick={() => { setMode(m); setError(null); }}
            className={`flex-1 py-2 transition-colors ${
              mode === m
                ? "bg-realis-600 text-white"
                : "text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700"
            }`}
          >
            {m === "login" ? "Se connecter" : "Créer un compte"}
          </button>
        ))}
      </div>

      {error && (
        <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-300 rounded-xl p-3 text-sm">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-3">
        <input
          type="email"
          required
          placeholder="votre@email.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="w-full px-3 py-2.5 border border-gray-200 dark:border-gray-600 rounded-lg text-sm
                     bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100
                     focus:outline-none focus:ring-2 focus:ring-realis-300 dark:focus:ring-realis-700
                     placeholder:text-gray-300 dark:placeholder:text-gray-500"
        />
        <input
          type="password"
          required
          minLength={mode === "register" ? 8 : 1}
          placeholder={mode === "register" ? "Mot de passe (8 caractères min)" : "Mot de passe"}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="w-full px-3 py-2.5 border border-gray-200 dark:border-gray-600 rounded-lg text-sm
                     bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100
                     focus:outline-none focus:ring-2 focus:ring-realis-300 dark:focus:ring-realis-700
                     placeholder:text-gray-300 dark:placeholder:text-gray-500"
        />
        <button
          type="submit"
          disabled={loading}
          className="w-full py-3 bg-realis-600 hover:bg-realis-700 disabled:opacity-40
                     text-white font-medium rounded-xl transition-colors"
        >
          {loading
            ? (mode === "login" ? "Connexion…" : "Création…")
            : (mode === "login" ? "Se connecter" : "Créer le compte")}
        </button>
      </form>
    </div>
  );
}

// ── Étape Scellement ──────────────────────────────────────────────────────────

function SealingStep() {
  return (
    <div className="py-8 flex flex-col items-center gap-4">
      <div className="w-12 h-12 rounded-full border-4 border-realis-200 dark:border-realis-800 border-t-realis-600 animate-spin" />
      <div className="text-center space-y-1">
        <p className="font-medium text-realis-700 dark:text-realis-300">Scellement en cours…</p>
        <p className="text-sm text-gray-400 dark:text-gray-500">
          Calcul SHA-256 · Horodatage RFC&nbsp;3161 · Chiffrement AES-256-GCM
        </p>
      </div>
    </div>
  );
}

// ── Étape Terminé ─────────────────────────────────────────────────────────────

function DoneStep({ record }: { record: SealResponse }) {
  const fmt = (iso: string) =>
    new Date(iso).toLocaleString("fr-FR", { timeZone: "UTC" }) + " UTC";

  return (
    <div className="space-y-5">
      <div className="text-center space-y-1">
        <div className="text-4xl text-green-600 dark:text-green-400">✓</div>
        <h2 className="text-lg font-semibold text-realis-700 dark:text-realis-300">Capture scellée</h2>
        <p className="text-sm text-gray-500 dark:text-gray-400">
          Votre preuve d&apos;intégrité et d&apos;antériorité a été générée.
        </p>
      </div>

      <div className="bg-realis-50 dark:bg-realis-900/30 border border-realis-100 dark:border-realis-800 rounded-xl p-4 space-y-1">
        <p className="text-xs text-realis-500 dark:text-realis-400 font-medium uppercase tracking-wide">Référence</p>
        <p className="font-mono text-sm text-realis-700 dark:text-realis-300 break-all">{record.id}</p>
      </div>

      <table className="w-full text-xs">
        <tbody className="divide-y divide-gray-50 dark:divide-gray-700">
          {([
            ["Scellé le",      fmt(record.sealedAt)],
            ["Horodatage TSA", record.tsaActive && record.tsaTimestamp
              ? fmt(record.tsaTimestamp)
              : "Non actif (développement)"],
            ["Taille",  formatSize(record.fileSizeBytes)],
            ["Format",  record.mimeType],
          ] as [string, string][]).map(([label, value]) => (
            <tr key={label}>
              <td className="py-1.5 pr-3 text-gray-400 dark:text-gray-500 font-medium whitespace-nowrap">{label}</td>
              <td className="py-1.5 text-gray-700 dark:text-gray-300">{value}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="space-y-1">
        <p className="text-xs font-medium text-gray-400 dark:text-gray-500 uppercase tracking-wide">Hash SHA-256</p>
        <p className="font-mono text-xs break-all text-gray-700 dark:text-gray-300 bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3">
          {record.sha256Hex}
        </p>
      </div>

      <div className="flex flex-col gap-3">
        <a
          href={certificateUrl(record.id)}
          download={`realis-certificat-${record.id}.pdf`}
          className="block w-full py-3 bg-realis-600 hover:bg-realis-700 text-white font-medium
                     rounded-xl transition-colors text-center"
        >
          Télécharger le certificat PDF
        </a>
        <a
          href={`/certificat/${record.id}`}
          className="block w-full py-2.5 border border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700
                     font-medium rounded-xl transition-colors text-center text-sm"
        >
          Voir le détail de la preuve
        </a>
        <a
          href={`/verifier?recordId=${record.id}`}
          className="block w-full py-2.5 text-sm text-realis-500 dark:text-realis-400 hover:underline text-center"
        >
          Vérifier ce fichier →
        </a>
      </div>
    </div>
  );
}

// ── Utilitaires ───────────────────────────────────────────────────────────────

function formatSize(bytes: number): string {
  if (bytes < 1024)         return `${bytes} o`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} Ko`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
}
