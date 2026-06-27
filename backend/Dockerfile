# ── Étape 1 : compilation Maven ──────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build
COPY pom.xml .
# Télécharge les dépendances en cache séparé (rebuild plus rapide)
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Étape 2 : image runtime allégée ──────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app

# Certificat TSA FreeTSA (embarqué pour la vérification offline)
RUN mkdir -p /app/tsa-certs
COPY --from=builder /build/src/main/resources/tsa-certs/ /app/tsa-certs/ 2>/dev/null || true

COPY --from=builder /build/target/realis-backend-*.jar app.jar

# Volume pour les captures chiffrées
VOLUME ["/data/captures"]

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
