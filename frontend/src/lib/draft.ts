import type { CaptureMetadata } from "@/components/VideoCapture";
import type { ConsentData } from "@/components/ConsentForm";
export interface CaptureDraft {
  id: string; owner: string; blob: Blob; metadata: CaptureMetadata; consent: ConsentData; savedAt: number;
}
function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open("realis-captures", 1);
    request.onupgradeneeded = () => request.result.createObjectStore("drafts");
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(new Error("Le navigateur ne permet pas la sauvegarde locale."));
  });
}
async function transaction<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await open();
  return new Promise((resolve, reject) => {
    const tx = db.transaction("drafts", mode);
    const request = action(tx.objectStore("drafts"));
    tx.oncomplete = () => { db.close(); resolve(request.result); };
    tx.onabort = tx.onerror = () => { db.close(); reject(new Error("Sauvegarde locale impossible : espace insuffisant ou accès refusé.")); };
  });
}
export async function saveDraft(draft: CaptureDraft): Promise<void> { await transaction("readwrite", s => s.put(draft, "pending")); }
export async function clearDraft(): Promise<void> { await transaction("readwrite", s => s.delete("pending")); }
export async function loadDraft(): Promise<CaptureDraft | null> {
  const draft = await transaction<CaptureDraft | undefined>("readonly", s => s.get("pending"));
  if (draft && Date.now() - draft.savedAt > 24 * 60 * 60 * 1000) { await clearDraft(); return null; }
  return draft ?? null;
}
export function downloadBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a"); a.href = url; a.download = name; a.click();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}
