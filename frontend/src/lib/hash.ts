/**
 * Calcule le SHA-256 d'un Blob via l'API Web Crypto (navigateur).
 * Le hash porte sur les octets bruts du blob, sans aucun ré-encodage.
 * Utilisé côté client pour affichage uniquement — le hash faisant foi
 * est calculé par le backend sur les octets reçus.
 */
export async function sha256Hex(blob: Blob): Promise<string> {
  const buffer = await blob.arrayBuffer();
  const hashBuffer = await crypto.subtle.digest("SHA-256", buffer);
  return Array.from(new Uint8Array(hashBuffer))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}
