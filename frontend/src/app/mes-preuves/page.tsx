"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { api, listMySeals, deleteSeal, downloadProof, type ProofPage, type ProofItem } from "@/lib/api";
export default function MesPreuvesPage() {
  const [data, setData] = useState<ProofPage | null>(null);
  const [query, setQuery] = useState("");
  const [folder, setFolder] = useState("");
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [editing, setEditing] = useState<ProofItem | null>(null);
  const [title, setTitle] = useState("");
  const [newFolder, setNewFolder] = useState("");
  useEffect(() => {
    let active = true; setLoading(true); setError("");
    const timer = setTimeout(() => {
      listMySeals(query, folder, page).then(result => { if (active) setData(result); })
        .catch(err => { if (active) setError(err.message); }).finally(() => { if (active) setLoading(false); });
    }, 250);
    return () => { active = false; clearTimeout(timer); };
  }, [query, folder, page, refresh]);
  const run = async (id: string, task: () => Promise<void>) => {
    setBusy(id); setError("");
    try { await task(); } catch (err) { setError(err instanceof Error ? err.message : "La demande a échoué"); }
    finally { setBusy(null); }
  };
  return <main className="max-w-3xl mx-auto p-6 space-y-6">
    <nav className="flex flex-wrap gap-4 text-sm underline"><Link href="/">Realis</Link><Link href="/nouveau">Nouvelle capture</Link><Link href="/compte">Mon compte</Link></nav>
    <h1 className="text-2xl font-bold">Mes preuves</h1>
    <div className="grid gap-4 sm:grid-cols-2">
      <label>Rechercher<input type="search" className="field" placeholder="Titre, dossier ou fichier" value={query} maxLength={160} onChange={e => {setQuery(e.target.value); setPage(0);}} /></label>
      <label>Dossier<input className="field" placeholder="Tous les dossiers" value={folder} maxLength={160} onChange={e => {setFolder(e.target.value); setPage(0);}} /></label>
    </div>
    {error && <div role="alert" className="panel text-red-700"><p>{error}</p><Link href="/compte" className="underline">Mon compte / Connexion</Link></div>}
    {loading && <p role="status">Chargement…</p>}
    {!loading && data?.content.length === 0 && <p>Aucune preuve pour cette recherche.</p>}
    {editing && <form className="panel" onSubmit={e => {e.preventDefault(); run(editing.record.id, async () => {
      await api(`/api/seal/${editing.record.id}/labels`, "PATCH", {title, folder: newFolder}); setEditing(null); setRefresh(x => x + 1);
    });}}>
      <h2 className="font-semibold">Classer cette preuve</h2>
      <p className="text-sm">Le titre et le dossier sont privés. Ils ne modifient pas le fichier scellé.</p>
      <label>Titre<input className="field" maxLength={160} value={title} onChange={e => setTitle(e.target.value)} /></label>
      <label>Dossier / logement<input className="field" maxLength={160} value={newFolder} onChange={e => setNewFolder(e.target.value)} /></label>
      <div className="flex gap-4"><button disabled={!!busy} className="action">Enregistrer</button><button type="button" onClick={() => setEditing(null)}>Annuler</button></div>
    </form>}
    <ul className="space-y-4">{data?.content.map(item => {
      const r = item.record;
      return <li key={r.id} className="panel">
        <Link className="font-semibold text-realis-600" href={`/certificat/${r.id}`}>{item.title || r.fileName}</Link>
        {item.folder && <button className="block underline text-sm" onClick={() => {setFolder(item.folder); setPage(0);}}>{item.folder}</button>}
        <p className="text-sm">{new Date(r.sealedAt).toLocaleString("fr-FR")} · {(r.fileSizeBytes / 1048576).toFixed(1)} Mo</p>
        <p className="text-xs">{r.tsaActive ? "Jeton d’horodatage disponible" : "Sans horodatage certifié"} · Conservation jusqu’au {new Date(new Date(r.sealedAt).getTime() + 365 * 86400000).toLocaleDateString("fr-FR")}</p>
        <div className="flex flex-wrap gap-4 text-sm">
          <button disabled={!!busy} className="underline" onClick={() => run(r.id, () => downloadProof(r.id, "original", r.fileName))}>Vidéo originale</button>
          <button disabled={!!busy} className="underline" onClick={() => run(r.id, () => downloadProof(r.id, "export", r.fileName))}>Exporter la preuve (.zip)</button>
          <button disabled={!!busy} className="underline" onClick={() => {setEditing(item); setTitle(item.title); setNewFolder(item.folder);}}>Classer / Renommer</button>
          <button disabled={!!busy} className="text-red-700" onClick={() => {
            if (!confirm("Retirer cette preuve et effacer sa capture ? Téléchargez l’archive avant de continuer. Cette action est définitive.")) return;
            run(r.id, async () => { await deleteSeal(r.id); setPage(0); setRefresh(x => x + 1); });
          }}>Supprimer</button>
        </div>{busy === r.id && <p role="status">Opération en cours…</p>}
      </li>;
    })}</ul>
    {data && data.totalPages > 1 && <nav className="flex justify-between items-center" aria-label="Pagination">
      <button disabled={page === 0 || loading} onClick={() => setPage(p => p - 1)}>← Précédent</button>
      <span>{page + 1} / {data.totalPages}</span>
      <button disabled={page + 1 >= data.totalPages || loading} onClick={() => setPage(p => p + 1)}>Suivant →</button>
    </nav>}
  </main>;
}
