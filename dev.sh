#!/usr/bin/env bash
set -e

cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "Erreur : .env introuvable."
  echo "Copiez .env.example, remplissez les valeurs, puis relancez."
  exit 1
fi

set -a
source .env
set +a

# Vérifie que postgres est accessible (natif ou Docker)
echo "Vérification de postgres sur localhost:5432..."
for i in $(seq 1 15); do
  if pg_isready -h localhost -p 5432 -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; then
    echo "Postgres prêt."
    break
  fi
  if [ "$i" -eq 15 ]; then
    echo "Erreur : postgres inaccessible sur localhost:5432 après 15s."
    echo "Lance : sudo service postgresql start"
    exit 1
  fi
  sleep 1
done

cleanup() {
  echo ""
  echo "Arrêt de l'environnement de développement..."
  kill 0
}
trap cleanup INT TERM

echo ""
echo "  Backend  → http://localhost:8080/api/health"
echo "  Frontend → http://localhost:3000"
echo "  (Ctrl+C pour tout arrêter)"
echo ""

(cd backend  && mvn spring-boot:run -Dspring-boot.run.profiles=local) &
(cd frontend && npm run dev) &

wait
