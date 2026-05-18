#!/usr/bin/env bash
set -euo pipefail

ADDONS_DIR="$HOME/skyblock_config/plugins/BentoBox/addons"
ADDONS_DATA_DIR="$HOME/skyblock_config/plugins/BentoBox/addons/BSkyBlock"
DB_DIR="$HOME/skyblock_config/plugins/BentoBox/database"
SERVER_DIR="$HOME/skyblock_config"
JAR=$(ls target/BSkyBlock-*.jar | grep -v sources | head -1)

echo "=== Reset BSkyBlock ==="
echo "JAR source : $JAR"
echo ""

# 1. Ancien JAR (directement dans addons/, pas dans le sous-dossier BSkyBlock/)
echo "[1/4] Suppression de l'ancien JAR..."
rm -f "$ADDONS_DIR"/BSkyBlock-*.jar

# 2. Bases de données îles
echo "[2/4] Nettoyage des bases de données..."
rm -f "$DB_DIR/Island"/*.json
rm -f "$DB_DIR/Players"/*.json
rm -f "$DB_DIR/Names"/*.json
rm -f "$DB_DIR/IslandLevels"/*.json
rm -f "$DB_DIR/IslandBlockCount"/*.json
rm -f "$DB_DIR/IslandDeletion"/*.json

# 3. Données paliers (notre fichier custom)
rm -f "$ADDONS_DATA_DIR/palier-data.yml"

# 4. Mondes BSkyBlock
echo "[3/4] Suppression des mondes BSkyBlock..."
rm -rf "$SERVER_DIR/bskyblock_world"
rm -rf "$SERVER_DIR/bskyblock_world_nether"
rm -rf "$SERVER_DIR/bskyblock_world_the_end"

# 5. Nouveau JAR (directement dans addons/, BentoBox le détecte là)
echo "[4/4] Copie du nouveau JAR..."
cp "$JAR" "$ADDONS_DIR/"

echo ""
echo "=== Fait. JAR installé : $(basename "$JAR") ==="
