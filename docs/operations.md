# Exploitation de Realis

## Configuration de production

- Java 17/21, Node 22, PostgreSQL 16. Le frontend doit être reconstruit si
  `NEXT_PUBLIC_API_URL` change. `FRONTEND_URL` désigne l’origine exacte autorisée
  par CORS et l’URL des liens envoyés par email.
- Terminer TLS devant les services ; ne pas exposer PostgreSQL. Le Compose
  le lie uniquement à localhost pour le développement.
- `TSA_PROVIDER=freetsa`, certificat PEM/DER lisible, certificat TSA contrôlé à
  l’instant du jeton. Le mode noop est interdit sans profil local/test.
- Configurer `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`,
  `SMTP_AUTH=true`, `SMTP_STARTTLS=true`, `SMTP_FROM`. Tester la réception réelle
  dans l’environnement de déploiement avant d’ouvrir les inscriptions.
- `STORAGE_QUOTA_BYTES` vaut 5368709120 (5 Gio) par compte. Ce quota inclut les
  captures en attente de purge. Le verrou PostgreSQL du compte sérialise les
  scellements concurrents pour empêcher de dépasser le quota.
- Garder les secrets hors du dépôt, distincts entre environnements. Une nouvelle
  clé AES ne déchiffre pas les captures existantes : ne jamais la remplacer
  directement. Une rotation exige migration contrôlée des fichiers et sauvegarde
  préalable de l’ancienne clé. La perte de cette clé rend les captures illisibles.
- Le stockage et les limites en mémoire supposent une instance applicative.
  Pour plusieurs instances, prévoir stockage partagé et limites partagées avant
  de répliquer. Le limiter intervient avant le parsing des uploads (30/min/IP,
  quatre uploads simultanés), avec une limite de fichier de 500 Mio.
- Ne pas servir les réponses `/api/verify/**` depuis un cache de proxy/CDN : un
  retrait doit être visible immédiatement. Ne pas journaliser les corps des
  requêtes contenant mots de passe ou tokens.

## Migration d’une installation existante

Sauvegarder d’abord. Flyway applique V2 sans modifier V1. Les preuves existantes
restent immuables ; les comptes existants doivent vérifier leur email avant de
créer de nouvelles captures. Les anciens consentements gardent leur texte et
une version nulle, plutôt que de prétendre qu’un nouveau texte a été accepté.

**Les anciennes preuves de plus de 365 jours et les anciennes suppressions
logiques deviennent éligibles à l’effacement dès le démarrage.** Examiner le
volume concerné, prévenir les propriétaires et exporter les captures utiles
avant le déploiement. Ne pas lancer une nouvelle version sur des données réelles
sans ce contrôle de migration.

Le contrat `GET /api/seal` est désormais paginé ; les verdicts de vérification
ont changé. Déployer backend et frontend ensemble. Le mode développement doit
explicitement activer `SPRING_PROFILES_ACTIVE=local` pour utiliser la TSA noop.

## Sauvegarde chiffrée

Prérequis : Python 3, GnuPG, Docker Compose, espace temporaire suffisant. Exécuter
sur un hôte dont le disque temporaire est chiffré : les fichiers de travail
contiennent un dump et la configuration avant chiffrement de l’archive.
Utiliser un mot de passe de sauvegarde aléatoire (au moins 32 octets), conservé
séparément du serveur et des archives, dans un fichier lisible uniquement par
l’opérateur. Ne jamais placer ce mot de passe dans une commande ou dans Git.

```sh
python3 scripts/backup.py --destination /srv/realis-backups \
  --passphrase-file /run/secrets/realis-backup-passphrase
```

Le script suspend les écritures en arrêtant le backend, sauvegarde PostgreSQL,
les captures chiffrées et `.env` (dont la clé AES), chiffre le tout avec GnuPG,
puis redémarre le backend seulement s’il tournait initialement. L’archive finale
n’est publiée qu’après chiffrement réussi. Les fichiers temporaires sont nettoyés.
Les erreurs font échouer la commande ; superviser sa sortie et son code retour.

Planifier quotidiennement, par exemple via un timer systemd local, avec une
fenêtre de maintenance et une alerte en cas d’échec. Le script retire ses archives
locales de plus de 30 jours après une sauvegarde réussie. **Configurer aussi une
expiration de 30 jours sur les copies distantes**. Planifier également
`python3 scripts/prune_backups.py /srv/realis-backups` chaque jour,
indépendamment du succès des sauvegardes. Une copie sur le même disque ne protège
pas d’une panne de l’hôte. Tester régulièrement la restauration.

## Restauration

Restaurer vers une installation isolée, avec volumes vides et le même nom de base.
Ne jamais exécuter ce test sur la base active.

```sh
python3 scripts/restore.py /srv/realis-backups/realis-DATE.tar.gpg \
  --passphrase-file /run/secrets/realis-backup-passphrase \
  --confirm-replace-database
```

L’outil laisse frontend/backend arrêtés et écrit la configuration sauvegardée dans
`restored-deployment.env` (non versionnée), sans écraser `.env`. Rétablir la clé AES
correspondante dans la configuration de la cible, puis retirer ce fichier de
travail après intégration au gestionnaire de secrets.

Avant de rouvrir le service :

1. Réappliquer les demandes d’effacement postérieures au snapshot à partir du
   registre d’exploitation conservé hors de cette sauvegarde. Ne pas remettre
   en ligne une sauvegarde ancienne si ce registre n’est pas disponible.
2. Laisser la purge d’expiration s’exécuter, puis vérifier les volumes.
3. Télécharger des originaux, vérifier SHA-256 et OpenSSL et tester un compte.
4. Vérifier que les liens supprimés restent indisponibles et tester les emails.

Les scripts ciblent les services et volumes du Compose fourni (captures dans
`/data/captures`). Adapter explicitement les chemins si la topologie change.

## Supervision

Sonder `/actuator/health` depuis l’hôte toutes les minutes : il inclut PostgreSQL,
SMTP et le volume de captures (écriture possible, au moins 1 Gio disponible).
La réponse publique n’expose aucun détail de configuration. Une purge en retard de plus d’une heure fait également passer le healthcheck à DOWN. Suivre en parallèle :

- erreurs « Effacement à réessayer », ancienneté des éléments à purger ;
- erreurs TSA et SMTP, échecs d’authentification, taux de réponses 429/503 ;
- espace disque (captures et répertoire temporaire), nombre d’uploads ;
- date et résultat de la dernière sauvegarde et restauration de contrôle.

La purge traite 100 éléments par minute ; augmenter sa capacité si la file
approche une heure. Une panne de stockage peut retarder l’effacement : alerter
et corriger, ne pas considérer une ligne marquée comme un fichier déjà effacé.

## Limites explicites

Les labels de rangement sont privés et modifiables. Les coordonnées et l’appareil
sont des déclarations du client, non des attestations matérielles. Le jeton porte
sur le fichier original, pas sur les métadonnées ou le PDF. Les copies téléchargées
ne sont pas révocables. Le certificat racine fourni dans une archive doit être
vérifié indépendamment par son destinataire.

Un brouillon IndexedDB est un secours local, pas une sauvegarde distante ; le
navigateur peut l’évincer, et la suppression du compte ne peut pas effacer les
brouillons sur un appareil déconnecté. L’interface propose de télécharger la
vidéo avant de l’envoyer.
