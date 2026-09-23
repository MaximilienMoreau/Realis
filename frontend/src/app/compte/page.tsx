"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { api, loginUser } from "@/lib/api";
import { clearAuth, getToken, saveAuth } from "@/lib/auth";
import { clearDraft } from "@/lib/draft";
interface Account { email: string; emailVerified: boolean; storageUsed: number; storageQuota: number }
export default function AccountPage() {
  const [account, setAccount] = useState<Account | null>(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [token, setToken] = useState("");
  const [action, setAction] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const refresh = () => api<Account>("/api/account").then(setAccount);
  useEffect(() => {
    const params = new URLSearchParams(window.location.hash.slice(1));
    setToken(params.get("token") ?? ""); setAction(params.get("action") ?? "");
    if (params.has("token")) history.replaceState(null, "", "/compte");
    if (getToken()) refresh().catch(err => setMessage(err.message));
  }, []);
  const run = async (task: () => Promise<void>) => {
    setBusy(true); setMessage("");
    try { await task(); } catch (err) { setMessage(err instanceof Error ? err.message : "La demande a échoué"); }
    finally { setBusy(false); }
  };
  const request = (kind: string) => run(async () => {
    const result = await api<{message: string}>(`/api/auth/${kind}`, "POST", { email: account?.email ?? email });
    setMessage(result.message);
  });
  return <main className="max-w-lg mx-auto p-6 space-y-6">
    <nav className="flex gap-4 text-sm underline"><Link href="/">Realis</Link><Link href="/mes-preuves">Mes preuves</Link><Link href="/nouveau">Nouvelle capture</Link></nav>
    <h1 className="text-2xl font-bold">Mon compte</h1>
    {message && <p role="status" className="rounded-xl bg-blue-50 text-blue-900 p-4">{message}</p>}
    {token && (action === "verify" || action === "reset") && <form className="panel" onSubmit={e => { e.preventDefault(); run(async () => {
      await api(`/api/auth/${action === "verify" ? "verify-email" : "reset-password"}`, "POST", { token, password });
      setToken(""); setPassword("");
      if (action === "reset") { clearAuth(); setAccount(null); }
      else if (getToken()) await refresh();
      setMessage(action === "verify" ? "Adresse vérifiée. Vous pouvez sceller vos captures." : "Mot de passe modifié. Reconnectez-vous.");
    }); }}>
      <h2 className="font-semibold">{action === "verify" ? "Vérifier mon adresse email" : "Choisir un nouveau mot de passe"}</h2>
      {action === "reset" && <label>Nouveau mot de passe<input className="field" type="password" autoComplete="new-password" required minLength={12} maxLength={72} value={password} onChange={e => setPassword(e.target.value)} /></label>}
      <button className="action" disabled={busy}>Confirmer</button>
    </form>}
    {account ? <section className="panel">
      <p>{account.email}</p><p>{account.emailVerified ? "Adresse email vérifiée" : "Adresse à vérifier avant le premier scellement"}</p>
      {!account.emailVerified && <button className="action" disabled={busy} onClick={() => request("resend-verification")}>Renvoyer l’email de vérification</button>}
      <p>Stockage : {(account.storageUsed / 1048576).toFixed(1)} Mo / {(account.storageQuota / 1073741824).toFixed(1)} Go</p>
      <progress className="w-full" value={account.storageUsed} max={account.storageQuota} />
      <button className="underline" disabled={busy} onClick={() => request("forgot-password")}>Changer mon mot de passe par email</button>
      <button className="underline block" onClick={() => { clearAuth(); setAccount(null); }}>Se déconnecter</button>
      <details className="border-t pt-4"><summary className="text-red-700 cursor-pointer">Supprimer mon compte</summary>
        <p className="text-sm my-3">Vos liens deviennent indisponibles immédiatement. Les captures et données actives sont effacées sous une heure ; les sauvegardes expirent sous 30 jours. Téléchargez vos preuves avant de continuer.</p>
        <form onSubmit={e => { e.preventDefault(); if (!confirm("Supprimer définitivement votre compte et toutes ses preuves ?")) return; run(async () => {
          await api("/api/account", "DELETE", {password}); clearAuth(); setAccount(null); setPassword("");
          await clearDraft().catch(() => {}); setMessage("Compte désactivé. Effacement des données en cours.");
        }); }}><label>Confirmer votre mot de passe<input className="field" type="password" required autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} /></label>
        <button className="action bg-red-700" disabled={busy}>Supprimer définitivement</button></form>
      </details>
    </section> : <section className="panel">
      <form className="space-y-4" onSubmit={e => {e.preventDefault(); run(async () => {
        const result = await loginUser(email, password); saveAuth(result.token, result.userId, result.email); setPassword(""); await refresh();
      });}}>
        <label>Email<input className="field" type="email" required autoComplete="email" value={email} onChange={e => setEmail(e.target.value)} /></label>
        <label>Mot de passe<input className="field" type="password" required autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} /></label>
        <button className="action" disabled={busy}>Se connecter</button>
      </form>
      <button disabled={busy || !email.includes("@")} className="underline" onClick={() => request("forgot-password")}>Mot de passe oublié</button>
      <Link href="/nouveau" className="block underline">Créer un compte</Link>
    </section>}
  </main>;
}
