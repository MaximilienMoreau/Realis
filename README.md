# Realis

Realis conserve des captures vidéo chiffrées et permet de vérifier leur intégrité
et leur antériorité grâce à SHA-256 et à un horodatage RFC 3161.
L’horodatage prouve l’existence des octets du fichier ; il ne certifie ni la scène
filmée, ni la position GPS, ni l’identité de l’appareil déclarées par le client.

## Fonctionnalités

- Inscription, connexion, vérification email et réinitialisation du mot de passe.
- Consentement versionné chargé depuis le serveur avant la capture.
- Vidéo avec prévisualisation, téléchargement local et brouillon IndexedDB pour
  reprendre un envoi interrompu ou une session expirée, sans créer de doublon.
- Scellement SHA-256, vérification de l’autorité TSA **avant** stockage, AES-256-GCM.
- Titres et dossiers privés, recherche et pagination (20 preuves par page).
- Téléchargement propriétaire de l’original et d’une archive ZIP autonome.
- Certificat et vérification publics via un identifiant aléatoire.
- Suppression, expiration automatique après 365 jours et suppression du compte.
- Quota de 5 Gio par compte, limitation des envois avant lecture du multipart.

Les titres/dossiers peuvent être modifiés sans modifier la preuve. Le GPS et le
navigateur sont déclaratifs et ne sont pas couverts par l’empreinte du fichier.
Le PDF distingue un horodatage vérifié d’un horodatage absent ou invalide.

## Démarrage

Prérequis : Docker Compose, ou Java 17/21, Maven, Node 22 et PostgreSQL 16.

```sh
cp .env.example .env
# Renseigner les mots de passe et générer des secrets distincts :
openssl rand -base64 48 # JWT_SECRET
openssl rand -base64 32 # ENCRYPTION_KEY
```

Pour tester les emails dans Mailpit, sans envoyer de messages externes :

```sh
docker compose -f docker-compose.yml -f docker-compose.local.yml up --build
```

Frontend : http://localhost:3000 ; API : http://localhost:8080 ; boîte mail
locale : http://localhost:8025. Vérifier l’email avant le premier scellement.
L’horodatage utilise FreeTSA et nécessite un accès réseau. `TSA_PROVIDER=noop`
n’est autorisé qu’avec le profil Spring `local` ou `test`, et ne produit jamais
un verdict d’horodatage vérifié.

Sans Docker : configurer dans `.env` `DB_URL=jdbc:postgresql://localhost:5432/realis`,
`STORAGE_PATH` vers un répertoire local et `TSA_CERT_PATH` vers le chemin absolu du
certificat du dépôt, puis `make dev`. Le serveur SMTP local doit être disponible
sur `localhost:1025`, ou remplacé par une configuration SMTP réelle.

En production, utiliser HTTPS, renseigner SMTP et appliquer les procédures de
[sauvegarde, restauration et supervision](docs/operations.md). `make dev-reset`
n’est pas une commande de migration : elle efface les volumes de développement.

## API

Les routes `/api/auth/**`, `/api/policy` et `/api/verify/**` sont publiques.
Les autres routes exigent `Authorization: Bearer <JWT>`. Les originaux et archives
sont strictement réservés au propriétaire. Les réponses publiques sensibles ne
sont pas mises en cache.

| Route | Fonction |
| --- | --- |
| `POST /api/auth/register`, `/login` | Inscription / connexion |
| `POST /api/auth/resend-verification`, `/forgot-password` | Demande d’email, réponse non révélatrice |
| `POST /api/auth/verify-email`, `/reset-password` | Consommation d’un jeton à usage unique |
| `GET /api/account` | Email, vérification, quota |
| `DELETE /api/account` | Suppression avec confirmation du mot de passe |
| `GET /api/policy` | Texte, version et durée de conservation |
| `POST /api/seal` | Sceller, email vérifié requis |
| `GET /api/seal?q=&folder=&page=0` | Preuves actives du propriétaire |
| `GET /api/seal/{id}` | Détail propriétaire |
| `PATCH /api/seal/{id}/labels` | Titre et dossier privés |
| `GET /api/seal/{id}/original`, `/export` | Original / ZIP |
| `DELETE /api/seal/{id}` | Retrait immédiat, puis effacement |
| `POST /api/verify` | Fichier et `recordId` optionnel |
| `GET /api/verify/{id}`, `/{id}/tsa`, `/{id}/certificate` | Métadonnées / jeton / PDF publics |
| `GET /actuator/health` | État base, disque des captures, SMTP |

Le multipart de scellement exige `file`, `captureId` (UUID stable pour chaque
capture), `consentAccepted=true`, `geolocConsented`, `policyVersion` et
`consentedAt` (date déclarée côté client). `mimeType`, `geolocLat`, `geolocLng`
et `deviceUa` sont facultatifs. La réception du consentement est aussi horodatée
par le serveur. La version doit correspondre à `/api/policy` ; un nouveau texte
ne peut pas être accepté silencieusement à la place de l’utilisateur.

Verdicts :

- `VERIFIE` : fichier identique et jeton TSA validé vers l’autorité configurée.
- `IDENTIQUE_SANS_HORODATAGE` : fichier identique mais horodatage non vérifié.
- `ALTERE` : fichier différent de la référence.
- `INCONNU` : aucune preuve disponible correspondant à la demande.
- `SUPPRIME` : référence retirée ou expirée, sans métadonnées exposées.

Le changement de mot de passe invalide tous les JWT précédents. Les tokens email
sont aléatoires, stockés uniquement sous forme d’empreinte et consommés une seule
fois. Le reset expire après 30 minutes ; la vérification email après 24 heures.

## Conservation

Les preuves expirent 365 jours après le scellement. La suppression retire
immédiatement les routes publiques et les téléchargements. Un traitement chaque
minute efface les fichiers, les métadonnées et les consentements associés par
lots de 100, avec reprise en cas d’échec. Surveiller le retard de purge avant
qu’il dépasse une heure. Seuls l’UUID et la date de retrait restent comme marqueur
sans contenu, GPS, nom de fichier ni lien vers le compte.

Le brouillon navigateur est supprimé après scellement réussi, sur demande, ou
lors de la prochaine ouverture après 24 heures. Aucun effacement à distance
n’est possible si l’appareil reste hors ligne. Éviter les appareils partagés.

Une suppression ne peut pas effacer les copies déjà téléchargées par des tiers
ni annuler un jeton TSA indépendant. Les sauvegardes doivent expirer sous 30 jours.

## Tests

```sh
mvn -f backend/pom.xml test
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend run type-check
npm --prefix frontend run build
cd frontend
npx playwright install chromium
npm run test:e2e
```

Les tests PostgreSQL sont activés par
`TEST_DB_URL=jdbc:postgresql://localhost:5432/realis_test` avec l’utilisateur et
le mot de passe `realis_test`. **Cette base doit être isolée** : les tests
tronquent ses tables entre scénarios et refusent tout autre nom de base.
La CI fournit ce service et exécute ces tests. Les tests navigateur couvrent
les formats bureau et mobile, avec interception réseau pour simuler les erreurs.

## Export indépendant

Télécharger l’archive depuis « Mes preuves », puis extraire et vérifier :

```sh
sha256sum -c sha256.txt
openssl ts -verify -token_in -in token.tsr -data original.bin -CAfile tsa-ca.crt
```

`-token_in` est requis : l’export contient un jeton CMS, pas une réponse TSP
complète. Vérifier indépendamment la provenance du certificat de confiance.
Le backend accepte les certificats PEM et DER et échoue au démarrage si l’ancre
configurée est illisible. Il ne se replie jamais sur un signataire non approuvé.

La génération PDF utilise iText Community ; voir sa licence AGPL dans les
dépendances. Les décisions d’hébergement et de distribution doivent tenir compte
de cette licence.
