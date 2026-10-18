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
export async function saveDraft(draft: CaptureDraft): Promise<void> {
  await transaction("readwrite", s => s.put(draft, draft.owner));
}
export async function clearDraft(owner: string): Promise<void> {
  await transaction("readwrite", s => {
    s.delete(owner);
    const legacy = s.get("pending");
    legacy.onsuccess = () => { if (legacy.result?.owner === owner) s.delete("pending"); };
    return legacy;
  });
}
export async function loadDraft(owner: string): Promise<CaptureDraft | null> {
  // Migrate the former shared slot only when it belongs to this account.
  const db = await open();
  return new Promise((resolve, reject) => {
    const tx = db.transaction("drafts", "readwrite");
    const store = tx.objectStore("drafts");
    let draft: CaptureDraft | null = null;
    const current = store.get(owner);
    current.onsuccess = () => {
      const legacy = store.get("pending");
      legacy.onsuccess = () => {
        const old = legacy.result as CaptureDraft | undefined;
        const value = current.result ?? (old?.owner === owner ? old : null);
        if (old?.owner === owner) store.delete("pending");
        if (value?.owner === owner && Date.now() - value.savedAt <= 24 * 60 * 60 * 1000) {
          draft = value; store.put(value, owner);
        } else store.delete(owner);
      };
    };
    tx.oncomplete = () => { db.close(); resolve(draft); };
    tx.onabort = tx.onerror = () => { db.close(); reject(new Error("Lecture du brouillon impossible.")); };
  });
}
export function downloadBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a"); a.href = url; a.download = name; a.click();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}
