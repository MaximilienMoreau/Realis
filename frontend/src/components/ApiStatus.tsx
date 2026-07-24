"use client";

import { useEffect, useState } from "react";

// Vérification côté navigateur (pas côté serveur Next.js) : NEXT_PUBLIC_API_URL désigne
// une adresse joignable depuis le poste client (ex. http://localhost:8080), pas depuis le
// conteneur frontend lui-même. En docker-compose, un fetch serveur vers cette URL échouerait
// systématiquement (le conteneur frontend n'a rien sur son propre localhost:8080 ; le backend
// n'est joignable en interne que via le nom de service "backend:8080").
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

type Status =
  | { state: "loading" }
  | { state: "up"; status: string; timestamp: string }
  | { state: "down" };

export default function ApiStatus() {
  const [status, setStatus] = useState<Status>({ state: "loading" });

  useEffect(() => {
    let cancelled = false;

    fetch(`${API_URL}/api/health`)
      .then((res) => res.json())
      .then((data: { status: string; timestamp: string }) => {
        if (!cancelled) setStatus({ state: "up", ...data });
      })
      .catch(() => {
        if (!cancelled) setStatus({ state: "down" });
      });

    return () => { cancelled = true; };
  }, []);

  if (status.state === "loading") return null;

  if (status.state === "down") {
    return (
      <p className="text-xs text-red-400 dark:text-red-500">
        Backend inaccessible : vérifiez docker-compose
      </p>
    );
  }

  return (
    <p className="text-xs text-green-600 dark:text-green-400">
      Backend : {status.status} · {status.timestamp}
    </p>
  );
}
