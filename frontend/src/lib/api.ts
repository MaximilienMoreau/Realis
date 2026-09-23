import { getToken, clearAuth } from "@/lib/auth";

export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

// Le token a expiré ou est invalide : le backend renvoie 401 (AuthenticationEntryPoint
// JSON dédié côté Spring Security). On lève un message fixe côté frontend plutôt que
// de dépendre de `data.message`, pour rester correct même si l'API change ce texte.
export class AuthExpiredError extends Error {
  constructor() {
    super("Session expirée, veuillez vous reconnecter.");
    this.name = "AuthExpiredError";
  }
}

async function parseJsonSafe(res: Response): Promise<any> {
  const text = await res.text();
  if (!text) return {};
  try {
    return JSON.parse(text);
  } catch {
    return {};
  }
}

// ── Types partagés ────────────────────────────────────────────────────────────

export interface SealResponse {
  id: string;
  sha256Hex: string;
  fileName: string;
  fileSizeBytes: number;
  mimeType: string;
  sealedAt: string;
  tsaTimestamp: string;
  tsaUrl: string;
  tsaActive: boolean;
  geolocLat: number | null;
  geolocLng: number | null;
  deleted: boolean;
  warning: string | null;
}

export type Verdict = "VERIFIE" | "IDENTIQUE_SANS_HORODATAGE" | "ALTERE" | "INCONNU" | "SUPPRIME";

export interface IntegrityCheck { passed: boolean; message: string }
export interface TsaCheck { valid: boolean; timestamp: string | null; message: string; isNoOp: boolean }

export interface VerificationResponse {
  verdict: Verdict;
  uploadedSha256: string;
  record: SealResponse | null;
  integrityCheck: IntegrityCheck;
  tsaCheck: TsaCheck | null;
}

// ── Auth ──────────────────────────────────────────────────────────────────────

export interface AuthResult {
  token: string;
  userId: string;
  email: string;
}

export async function registerUser(email: string, password: string): Promise<AuthResult> {
  const res = await fetch(`${API_URL}/api/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message ?? "Erreur lors de l'inscription");
  return data as AuthResult;
}

export async function loginUser(email: string, password: string): Promise<AuthResult> {
  const res = await fetch(`${API_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message ?? "Identifiants invalides");
  return data as AuthResult;
}

// ── Scellement ────────────────────────────────────────────────────────────────

export async function sealCapture(draft: import("./draft").CaptureDraft, onProgress: (value: number) => void): Promise<SealResponse> {
  const form = new FormData();
  const ext = draft.metadata.mimeType.includes("mp4") ? "mp4" : "webm";
  form.append("file", draft.blob, `capture-${draft.id}.${ext}`);
  form.append("captureId", draft.id);
  form.append("mimeType", draft.metadata.mimeType);
  form.append("deviceUa", draft.metadata.deviceUa);
  if (draft.metadata.geolocLat != null) form.append("geolocLat", String(draft.metadata.geolocLat));
  if (draft.metadata.geolocLng != null) form.append("geolocLng", String(draft.metadata.geolocLng));
  form.append("consentAccepted", "true");
  form.append("geolocConsented", String(draft.consent.geolocConsented));
  form.append("policyVersion", draft.consent.policyVersion);
  form.append("consentedAt", draft.consent.consentedAt);
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", `${API_URL}/api/seal`);
    const token = getToken();
    if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    xhr.timeout = 15 * 60 * 1000;
    xhr.upload.onprogress = e => { if (e.lengthComputable) onProgress(Math.round(e.loaded / e.total * 100)); };
    xhr.onerror = () => reject(new Error("Connexion interrompue. Votre capture est conservée pour réessayer."));
    xhr.ontimeout = () => reject(new Error("Envoi trop long. Vous pouvez renvoyer la même capture sans créer de doublon."));
    xhr.onload = () => {
      if (xhr.status === 401) { clearAuth(); reject(new AuthExpiredError()); return; }
      let data; try { data = JSON.parse(xhr.responseText); } catch { data = {}; }
      if (xhr.status >= 200 && xhr.status < 300) resolve(data);
      else reject(new Error(data.message ?? "Échec de l’envoi. Votre capture est conservée."));
    };
    xhr.send(form);
  });
}

export async function api<T>(path: string, method = "GET", body?: unknown): Promise<T> {
  const token = getToken();
  const res = await fetch(`${API_URL}${path}`, { method,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { "Content-Type": "application/json" } : {}) },
    body: body ? JSON.stringify(body) : undefined });
  if (res.status === 401) { clearAuth(); throw new AuthExpiredError(); }
  const data = await parseJsonSafe(res);
  if (!res.ok) throw new Error(data.message ?? "La demande a échoué");
  return data;
}
export async function downloadProof(id: string, kind: "original" | "export", fileName: string) {
  const res = await fetch(`${API_URL}/api/seal/${id}/${kind}`, { headers: { Authorization: `Bearer ${getToken() ?? ""}` } });
  if (res.status === 401) { clearAuth(); throw new AuthExpiredError(); }
  if (!res.ok) throw new Error("Téléchargement impossible. Vérifiez votre connexion et l’accès à cette preuve.");
  const { downloadBlob } = await import("./draft");
  downloadBlob(await res.blob(), kind === "export" ? `realis-${id}.zip` : fileName);
}
export interface ProofItem { record: SealResponse; title: string; folder: string }
export interface ProofPage { content: ProofItem[]; totalPages: number; totalElements: number; number: number }

export async function getSealRecord(id: string): Promise<SealResponse> {
  const res = await fetch(`${API_URL}/api/verify/${id}`);
  if (!res.ok) throw new Error("Enregistrement introuvable");
  return res.json();
}

export function certificateUrl(id: string): string {
  return `${API_URL}/api/verify/${id}/certificate`;
}

export function tsaTokenUrl(id: string): string {
  return `${API_URL}/api/verify/${id}/tsa`;
}

export async function listMySeals(q = "", folder = "", page = 0): Promise<ProofPage> {
  return api(`/api/seal?${new URLSearchParams({ q, folder, page: String(page) })}`);
}

/**
 * Suppression logique : invalide définitivement la preuve.
 * Réservé au propriétaire (JWT requis).
 */
export async function deleteSeal(id: string): Promise<SealResponse> {
  const token = getToken();
  if (!token) throw new AuthExpiredError();

  const res = await fetch(`${API_URL}/api/seal/${id}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
  if (res.status === 401) {
    clearAuth();
    throw new AuthExpiredError();
  }
  const data = await parseJsonSafe(res);
  if (!res.ok) throw new Error(data.message ?? "Erreur lors de la suppression");
  return data as SealResponse;
}

// ── Vérification ──────────────────────────────────────────────────────────────

export async function verifyFile(
  file: File,
  recordId?: string
): Promise<VerificationResponse> {
  const form = new FormData();
  form.append("file", file);
  if (recordId?.trim()) form.append("recordId", recordId.trim());

  const res = await fetch(`${API_URL}/api/verify`, { method: "POST", body: form });
  const data = await res.json();
  if (!res.ok && res.status !== 404) {
    throw new Error(data.message ?? "Erreur serveur");
  }
  return data as VerificationResponse;
}
