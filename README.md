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
mvn spring-boot:run
```

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

# Vérifier avec openssl
openssl ts -verify -in token.tsr -data fichier_original.webm \
  -CAfile freetsa-ca.crt
```

<br>

## RGPD

- Consentement granulaire (géoloc opt-in) horodaté avant toute capture.
- Les captures sont chiffrées at rest (AES-256-GCM).
- Endpoint de suppression logique disponible (avec avertissement : la suppression invalide la preuve).
- Minimisation des données : seuls les champs nécessaires à la preuve sont collectés.

<br>

<div align="center">

*Realis : MVP en développement*

</div>
