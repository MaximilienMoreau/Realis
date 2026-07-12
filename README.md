<div align="center">

# Realis

**Certification du réel**

Service de certification de l'intégrité et de l'antériorité de captures numériques,
basé sur un hash SHA-256 et un horodatage cryptographique RFC 3161 (TSP).

![Next.js](https://img.shields.io/badge/Next.js-14-000000?style=flat-square&logo=next.js&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white)
![RFC 3161](https://img.shields.io/badge/Horodatage-RFC%203161-2D52C4?style=flat-square)
![Statut](https://img.shields.io/badge/Statut-MVP-lightgrey?style=flat-square)

</div>

<br>

> **Important** : le certificat Realis est une preuve d'intégrité et d'antériorité
> (hash + horodatage RFC 3161). Il ne constitue pas un acte authentique au sens juridique.

<br>

## Sommaire

- [Démarrage rapide](#démarrage-rapide)
- [Architecture](#architecture)
- [Endpoints principaux](#endpoints-principaux)
- [Format des erreurs](#format-des-erreurs)
- [Développement local](#développement-local-sans-docker)
- [Vérification indépendante d'un jeton TSA](#vérification-indépendante-dun-jeton-tsa)
- [RGPD](#rgpd)

<br>

## Démarrage rapide

### Prérequis

- Docker ≥ 24 et Docker Compose ≥ 2.20
- `openssl` (pour générer les secrets)

### 1. Configurer l'environnement

```bash
cp .env.example .env
```

Éditer `.env` et remplacer **toutes** les valeurs `CHANGE_ME` :

```bash
# Générer le secret JWT (minimum 32 caractères)
openssl rand -base64 48

# Générer la clé de chiffrement AES-256 (32 octets en Base64)
openssl rand -base64 32
```

### 2. Lancer la stack

```bash
docker compose up --build
```

<div align="center">

| Service | URL |
|:---|:---|
| **Frontend** | [http://localhost:3000](http://localhost:3000) |
| **Backend API** | [http://localhost:8080/api/health](http://localhost:8080/api/health) |
| **PostgreSQL** | `localhost:5432` |

</div>

### 3. Vérifier que tout tourne

```bash
# Healthcheck backend
curl http://localhost:8080/api/health

# Logs en temps réel
docker compose logs -f backend
```

<br>

## Architecture

```
Realis/
├── frontend/   Next.js 14 (App Router), TypeScript, Tailwind (PWA)
├── backend/    Spring Boot 3 (Java 21), API REST
└── docker-compose.yml
```

### Stack technique

<div align="center">

| Composant | Technologie | Raison |
|:---|:---|:---|
| Frontend | Next.js 14 + Tailwind | PWA, App Router, SSR |
| Backend | Spring Boot 3, Java 21 | Robustesse, écosystème crypto Java |
| Base de données | PostgreSQL 16 | ACID, triggers d'immuabilité |
| Horodatage | RFC 3161 (TSP) via FreeTSA | Juridiquement opposable, standard eIDAS |
| Hash | SHA-256 (JCA) | Standard, vérifiable par tiers |
| PDF | iText 8 Community (AGPL) | PDF/A, API fluide |
| Chiffrement at rest | AES-256-GCM (JCA) | Captures chiffrées sur disque |
| Auth | JWT (JJWT) | Stateless, découplé |

</div>

### Flux de scellement

```
Caméra → blob vidéo → upload backend
  → SHA-256 sur octets bruts
  → RFC 3161 token (FreeTSA)
  → stockage chiffré AES-256
  → certificat PDF
  → retour identifiant + lien de vérification
```

<br>

## Endpoints principaux

<div align="center">

| Méthode | Route | Auth | Description |
|:---|:---|:---|:---|
| `POST` | `/api/auth/register` | — | Création de compte |
| `POST` | `/api/auth/login` | — | Connexion |
| `POST` | `/api/seal` | JWT | Scelle une capture (multipart) |
| `GET` | `/api/seal` | JWT | Liste les scellements de l'utilisateur connecté |
| `GET` | `/api/seal/{id}` | JWT, propriétaire | Détail d'un scellement |
| `DELETE` | `/api/seal/{id}` | JWT, propriétaire | Suppression logique (invalide la preuve) |
| `POST` | `/api/verify` | — | Vérifie un fichier (verdict AUTHENTIQUE / ALTÉRÉ / INCONNU) |
| `GET` | `/api/verify/{id}` | — | Métadonnées publiques d'un scellement |
| `GET` | `/api/verify/{id}/tsa` | — | Jeton RFC 3161 brut (`.tsr`) |
| `GET` | `/api/verify/{id}/certificate` | — | Certificat PDF (lien partageable) |
| `GET` | `/api/health` | — | Healthcheck applicatif |
| `GET` | `/actuator/health` | — | Healthcheck Spring Boot Actuator (supervision externe) |

</div>

`/api/auth/**` et `/api/verify/**` sont publics par conception : la page de
vérification et le certificat PDF doivent être consultables sans compte, via un
simple lien. `/api/seal/**` (hors scellement en lui-même) exige un JWT.

<br>

## Format des erreurs

Toute erreur renvoie un corps JSON homogène :

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Enregistrement introuvable : ...",
  "timestamp": "2026-07-05T10:00:00Z"
}
```

<div align="center">

| Code | Cas |
|:---|:---|
| `400` | Requête invalide (validation, email déjà utilisé) |
| `401` | JWT absent, invalide ou expiré |
| `403` | Accès à une ressource dont on n'est pas propriétaire |
| `404` | Ressource introuvable |
| `409` | Conflit (ex. suppression d'un enregistrement déjà supprimé, email déjà pris en cas de course concurrente) |
| `413` | Fichier trop volumineux (> 500 Mo) |
| `429` | Trop de tentatives (`/api/auth/login`, `/api/auth/register`) |
| `503` | TSA (FreeTSA) temporairement indisponible |

</div>

<br>

## Développement local (sans Docker)

### Backend

```bash
cd backend
# Démarrer un Postgres local d'abord, puis :
DB_URL=jdbc:postgresql://localhost:5432/realis \
DB_USER=realis_user \
DB_PASSWORD=xxx \
JWT_SECRET=xxx \
STORAGE_PATH=/tmp/realis-captures \
ENCRYPTION_KEY=xxx \
FRONTEND_URL=http://localhost:3000 \
mvn spring-boot:run
```

`TSA_PROVIDER`, `TSA_URL` et `TRUST_FORWARDED_FOR` ont des valeurs par défaut
raisonnables (voir `application.yml`) et peuvent être omis. Attention en revanche
à `TSA_CERT_PATH` : sa valeur par défaut (`/app/tsa-certs/freetsa-ca.crt`) est un
chemin absolu valide uniquement dans le conteneur Docker. Hors Docker, pointez-le
vers le fichier versionné du dépôt, ex. `TSA_CERT_PATH=$(pwd)/src/main/resources/tsa-certs/freetsa-ca.crt`,
sans quoi la vérification TSA locale se fait sans ancre de confiance (avertissement
en log, pas d'échec).

### Frontend

```bash
cd frontend
npm install
NEXT_PUBLIC_API_URL=http://localhost:8080 npm run dev
```

<br>

## Vérification indépendante d'un jeton TSA

Un jeton RFC 3161 stocké en DB peut être vérifié **sans Realis** :

```bash
# Exporter le token via l'API
curl -o token.tsr http://localhost:8080/api/verify/{id}/tsa

# Vérifier avec openssl (freetsa-ca.crt est versionné dans le dépôt :
# backend/src/main/resources/tsa-certs/freetsa-ca.crt)
openssl ts -verify -in token.tsr -data fichier_original.webm \
  -CAfile backend/src/main/resources/tsa-certs/freetsa-ca.crt
```

<br>

## RGPD

- Consentement granulaire (géoloc opt-in) horodaté avant toute capture.
- Les captures sont chiffrées at rest (AES-256-GCM).
- Endpoint de suppression logique disponible (avec avertissement : la suppression invalide la preuve).
- Minimisation des données : seuls les champs nécessaires à la preuve sont collectés.
- Rate-limiting basique (10 requêtes/minute/IP) sur `/api/auth/login` et
  `/api/auth/register`, et (30 requêtes/minute/IP) sur `/api/verify`, pour
  limiter le bruteforce et les abus (en mémoire, mono-instance, à remplacer
  par un backend partagé type Redis en cas de scale-out).
- L'IP cliente utilisée pour le rate-limiting et le journal de consentement est
  celle de la connexion TCP directe par défaut. Derrière un reverse proxy de
  confiance, activer `TRUST_FORWARDED_FOR=true` (voir `.env.example`) pour lire
  `X-Forwarded-For`, à n'activer que si ce proxy écrase l'en-tête entrant,
  sinon un client peut usurper une IP arbitraire.

<br>

<div align="center">

*Realis : MVP en développement*

</div>
