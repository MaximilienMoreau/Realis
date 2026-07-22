"use client";

import { useState } from "react";

export interface ConsentData {
  geolocConsented: boolean;
  purposeText: string;
  retentionDays: number;
  consentedAt: string; // ISO
}

const PURPOSE_TEXT =
  "Enregistrement d'un état des lieux immobilier à des fins de preuve " +
  "d'intégrité et d'antériorité. Les données sont conservées 365 jours " +
  "et peuvent être supprimées sur demande (la suppression invalide la preuve).";

interface Props {
  onConsent: (data: ConsentData) => void;
}

export default function ConsentForm({ onConsent }: Props) {
  const [geolocConsented, setGeolocConsented] = useState(false);
  const [purposeRead, setPurposeRead]         = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!purposeRead) return;
    onConsent({
      geolocConsented,
      purposeText: PURPOSE_TEXT,
      retentionDays: 365,
      consentedAt: new Date().toISOString(),
    });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-realis-700 dark:text-realis-300 mb-1">
          Avant de commencer
        </h2>
        <p className="text-sm text-gray-500 dark:text-gray-400">
          Realis collecte les données suivantes pour constituer votre preuve.
        </p>
      </div>

      <div className="bg-realis-50 dark:bg-realis-900/30 border border-realis-100 dark:border-realis-800 rounded-xl p-4 text-sm text-gray-700 dark:text-gray-300 leading-relaxed">
        <p className="font-medium text-realis-700 dark:text-realis-300 mb-1">Finalité de la collecte</p>
        <p>{PURPOSE_TEXT}</p>
      </div>

      <div className="space-y-2 text-sm text-gray-600 dark:text-gray-300">
        <p className="font-medium text-gray-700 dark:text-gray-200">Données collectées :</p>
        <ul className="list-disc list-inside space-y-1 pl-1">
          <li>Vidéo de la capture (chiffrée au repos)</li>
          <li>Hash SHA-256 des octets bruts</li>
          <li>Horodatage cryptographique RFC&nbsp;3161</li>
          <li>Modèle / navigateur de l&apos;appareil</li>
        </ul>
      </div>

      <label className="flex items-start gap-3 cursor-pointer group">
        <input
          type="checkbox"
          checked={geolocConsented}
          onChange={(e) => setGeolocConsented(e.target.checked)}
          className="mt-0.5 w-4 h-4 accent-realis-600 cursor-pointer"
        />
        <span className="text-sm text-gray-700 dark:text-gray-300">
          <span className="font-medium">Géolocalisation</span>{" "}
          <span className="text-gray-400 dark:text-gray-500">(optionnel)</span>
          <br />
          <span className="text-gray-500 dark:text-gray-400 text-xs">
            J&apos;autorise l&apos;inclusion de ma position GPS dans la preuve.
            Cette position sera visible par toute personne disposant du lien
            du certificat (lien public, sans authentification requise).
          </span>
        </span>
      </label>

      <label className="flex items-start gap-3 cursor-pointer">
        <input
          type="checkbox"
          required
          checked={purposeRead}
          onChange={(e) => setPurposeRead(e.target.checked)}
          className="mt-0.5 w-4 h-4 accent-realis-600 cursor-pointer"
        />
        <span className="text-sm text-gray-700 dark:text-gray-300">
          J&apos;ai lu et compris la finalité et les conditions de collecte.
        </span>
      </label>

      <p className="text-xs text-gray-400 dark:text-gray-500 leading-relaxed">
        Le certificat constitue une preuve d&apos;intégrité et d&apos;antériorité
        (hash SHA-256 + horodatage RFC&nbsp;3161). Il ne constitue pas un acte
        authentique au sens juridique.
      </p>

      <button
        type="submit"
        disabled={!purposeRead}
        className="w-full py-3 bg-realis-600 hover:bg-realis-700 disabled:opacity-40
                   text-white font-medium rounded-xl transition-colors"
      >
        Accepter et commencer la capture
      </button>
    </form>
  );
}
