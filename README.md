# Realis — Certification du réel (MVP)

Service de certification de l'intégrité et de l'antériorité de captures numériques.
Hash SHA-256 + horodatage cryptographique RFC 3161 (TSP).

> **Important** : le certificat Realis est une preuve d'intégrité et d'antériorité
> (hash + horodatage RFC 3161). Il ne constitue pas un acte authentique au sens juridique.

---

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

- Frontend : [http://localhost:3000](http://localhost:3000)
- Backend API : [http://localhost:8080/api/health](http://localhost:8080/api/health)
- PostgreSQL : localhost:5432

### 3. Vérifier que tout tourne

```bash
# Healthcheck backend
curl http://localhost:8080/api/health

# Logs en temps réel
docker compose logs -f backend
```

---

## Architecture

```
Realis/
├── frontend/   Next.js 14 (App Router) + TypeScript + Tailwind — PWA
├── backend/    Spring Boot 3 (Java 21) — API REST
└── docker-compose.yml
```

### Stack technique

| Composant | Technologie | Raison |
|-----------|-------------|--------|
| Frontend | Next.js 14 + Tailwind | PWA, App Router, SSR |
| Backend | Spring Boot 3, Java 21 | Robustesse, écosystème crypto Java |
| Base de données | PostgreSQL 16 | ACID, triggers d'immuabilité |
| Horodatage | RFC 3161 (TSP) via FreeTSA | Juridiquement opposable, standard eIDAS |
| Hash | SHA-256 (JCA) | Standard, vérifiable par tiers |
| PDF | iText 8 Community (AGPL) | PDF/A, API fluide |
| Chiffrement at rest | AES-256-GCM (JCA) | Captures chiffrées sur disque |
| Auth | JWT (JJWT) | Stateless, découplé |

### Flux de scellement

```
Caméra → blob vidéo → upload backend
  → SHA-256 sur octets bruts
  → RFC 3161 token (FreeTSA)
  → stockage chiffré AES-256
  → certificat PDF
  → retour identifiant + lien de vérification
```

---

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

---

## Vérification indépendante d'un jeton TSA

Un jeton RFC 3161 stocké en DB peut être vérifié **sans Realis** :

```bash
# Exporter le token via l'API
curl -o token.tsr http://localhost:8080/api/verify/{id}/tsa

# Vérifier avec openssl
openssl ts -verify -in token.tsr -data fichier_original.webm \
  -CAfile freetsa-ca.crt
```

---

## RGPD

- Consentement granulaire (géoloc opt-in) horodaté avant toute capture.
- Les captures sont chiffrées at rest (AES-256-GCM).
- Endpoint de suppression logique disponible (avec avertissement : la suppression invalide la preuve).
- Minimisation des données : seuls les champs nécessaires à la preuve sont collectés.
