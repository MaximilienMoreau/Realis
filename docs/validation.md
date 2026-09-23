# Validation de la fiabilisation

Vérifications exécutées sur le code final :

| Vérification | Résultat |
| --- | --- |
| Maven, tests unitaires + PostgreSQL 16 réel | 78 réussis, 1 test réseau FreeTSA ignoré |
| OpenSSL sur un jeton CMS généré par la TSA de test | Réussi avec `-token_in` |
| Certificat PEM réellement livré dans le dépôt | Chargement réussi, absence de confiance rejetée |
| Playwright Chromium bureau + émulation Pixel 7 | 10 réussis |
| Archives avec GnuPG réel, restauration et rétention | 3 réussis |
| ESLint | Réussi |
| TypeScript / types de routes Next | Réussi |
| Build de production Next.js 15.5.26 / React 19.3 | Réussi |
| Audit npm après mise à jour | 0 vulnérabilité signalée |
| `git diff --check` | Réussi |

Les tests PostgreSQL couvrent reprise sans doublon, chiffrement/déchiffrement,
export ZIP, contrôle du propriétaire, immuabilité SQL, classement privé,
suppression/expiration, quotas, consentement, vérification email, récupération
à usage unique et révocation des anciennes sessions. Une base dédiée
`realis_test` est créée séparément de toute base utilisateur.

Les tests navigateur couvrent les interruptions réseau, la session expirée,
la caméra refusée, le rangement et l’enregistrement effectif par MediaRecorder
avec une source vidéo synthétique. Les réponses API sont interceptées pour
rendre les scénarios déterministes ; le backend est testé séparément avec la
vraie base PostgreSQL.

Le test GnuPG chiffre/déchiffre réellement les archives de données fictives.
Les commandes Docker y sont simulées : le daemon Docker est inaccessible dans
cet environnement. Une restauration du Compose sur un hôte de déploiement et
un essai sur de vrais appareils iOS/Android restent à faire avant diffusion.
Aucun email réel ni aucune donnée de production n’ont été utilisés.

La mise à jour Next suit les versions corrigées indiquées dans l’[avis officiel
Next.js](https://github.com/vercel/next.js/security/advisories/GHSA-2xp9-vwfh-vxw4).
L’override PostCSS force aussi Next à utiliser la branche corrigée 8.5, vérifiée
par le build et les tests, au lieu de sa dépendance imbriquée ancienne.
