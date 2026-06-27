import { getToken } from "@/lib/auth";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

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

export type Verdict = "AUTHENTIQUE" | "ALTERE" | "INCONNU";

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

export async function sealCapture(params: {
  blob: Blob;
  mimeType: string;
  geolocLat?: number;
  geolocLng?: number;
  deviceUa?: string;
}): Promise<SealResponse> {
  const ext = params.mimeType.includes("mp4") ? "mp4" : "webm";
  const form = new FormData();
  form.append(
    "file",
    new File([params.blob], `capture-${Date.now()}.${ext}`, { type: params.mimeType })
  );
  form.append("mimeType", params.mimeType);
  if (params.geolocLat != null) form.append("geolocLat", params.geolocLat.toString());
  if (params.geolocLng != null) form.append("geolocLng", params.geolocLng.toString());
  if (params.deviceUa)          form.append("deviceUa",  params.deviceUa);

  const token = getToken();
  const res = await fetch(`${API_URL}/api/seal`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message ?? "Erreur lors du scellement");
  return data;
}

export async function getSealRecord(id: string): Promise<SealResponse> {
  const res = await fetch(`${API_URL}/api/seal/${id}`);
  if (!res.ok) throw new Error("Enregistrement introuvable");
  return res.json();
}

export function certificateUrl(id: string): string {
  return `${API_URL}/api/seal/${id}/certificate`;
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
