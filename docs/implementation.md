# Fiabilisation de Realis

Implémenté :
- TSA PEM/DER, confiance obligatoire, validation avant stockage, export OpenSSL.
- Verdicts explicites, PDF validé et portée déclarative du GPS/appareil.
- Originaux et archive de preuve accessibles au propriétaire uniquement.
- Brouillon IndexedDB, prévisualisation, reprise après échec/session expirée,
  progression et idempotence par identifiant de capture.
- Consentement versionné, expiration à 365 jours, effacement et suppression du compte.
- Vérification email et récupération par SMTP, configuration locale Mailpit.
- Titres, dossiers privés et recherche paginée.
- Quotas sérialisés par compte, limitation des uploads avant parsing,
  sauvegarde chiffrée/restauration, expiration des archives et healthchecks.
- Régressions unitaires, PostgreSQL réel, OpenSSL et parcours navigateur.
- Migration Next.js 15 / React 19 et dépendances npm corrigées.

À configurer sur le déploiement : SMTP, TLS, emplacement/mot de passe des
sauvegardes et leur planification. Aucun déploiement n’a été effectué pendant ce
travail. Voir operations.md pour la migration des anciennes données, la rotation
contrôlée des clés et la restauration sans réintroduire des données supprimées.

Les tests d’archives utilisent réellement GnuPG avec données fictives ; les
commandes Docker sont simulées car ce daemon n’est pas accessible dans
l’environnement de développement. PostgreSQL a été testé sur une instance
locale isolée. Les navigateurs utilisent Chromium bureau et émulation mobile ;
un contrôle sur de vrais appareils iOS/Android reste recommandé avant diffusion.
