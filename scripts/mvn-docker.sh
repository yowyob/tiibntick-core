#!/usr/bin/env bash
# scripts/mvn-docker.sh — Lance Maven dans un conteneur Docker sans créer
# de fichiers appartenant à root dans le repo ou dans ~/.m2.
#
# POURQUOI --user est obligatoire
# ────────────────────────────────────────────────────────────────────────────
# L'image maven:3.9-eclipse-temurin-21 tourne en root à l'intérieur du
# conteneur. Sans --user, tout fichier écrit dans les volumes montés
# (target/, ~/.m2/) se retrouve avec owner root:root sur le système hôte.
# Ces fichiers bloquent ensuite les builds Maven lancés par l'utilisateur
# courant : permission denied sur target/generated-sources, impossibilité de
# relancer mvn clean, etc.
# --user "$(id -u):$(id -g)" garantit que le processus Maven tourne avec
# l'UID/GID de l'utilisateur hôte, donc tous les fichiers créés sont bien
# possédés par cet utilisateur.
#
# USAGE
# ────────────────────────────────────────────────────────────────────────────
#   scripts/mvn-docker.sh [<goals-maven>...]
#
# Exemples :
#   scripts/mvn-docker.sh clean install -DskipTests
#   scripts/mvn-docker.sh -pl tnt-bootstrap -am install -DskipTests -Denforcer.skip=true
#
# Variables d'environnement reconnues :
#   MVN_IMAGE   image Docker Maven à utiliser
#               (défaut : maven:3.9-eclipse-temurin-21)
#   MAVEN_OPTS  options JVM supplémentaires passées à Maven

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
M2_DIR="${HOME}/.m2"
MVN_IMAGE="${MVN_IMAGE:-maven:3.9-eclipse-temurin-21}"

# Créer ~/.m2 si absent (docker volume mount échoue sinon)
mkdir -p "${M2_DIR}"

exec docker run --rm \
  --user "$(id -u):$(id -g)" \
  -v "${REPO_ROOT}":/ws \
  -v "${M2_DIR}":/root/.m2 \
  -e MAVEN_CONFIG=/root/.m2 \
  -e MAVEN_OPTS="${MAVEN_OPTS:-}" \
  -w /ws \
  "${MVN_IMAGE}" \
  mvn -Duser.home=/root "$@"
