#!/bin/bash
#═══════════════════════════════════════════════════════════════
# Script de Déploiement AROME vers le VPS
# Usage: bash deploy_arome_to_vps.sh
#═══════════════════════════════════════════════════════════════

# Configuration - À ADAPTER
VPS_USER="ubuntu"
VPS_HOST="VOTRE_IP_VPS"  # Remplacer par l'IP de votre VPS
VPS_PROJECT_DIR="/home/ubuntu/soaringmeteo"

# Couleurs pour les messages
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "╔════════════════════════════════════════════════════════════╗"
echo "║      Déploiement Scripts AROME vers VPS                   ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Vérifier que nous sommes dans le bon répertoire
if [ ! -f "scripts/test_arome_availability.sh" ]; then
    echo -e "${RED}❌ ERREUR: Exécutez ce script depuis /home/user/HaizeHegoa${NC}"
    exit 1
fi

echo -e "${YELLOW}⚠️  Configuration actuelle:${NC}"
echo "  VPS: $VPS_USER@$VPS_HOST"
echo "  Répertoire cible: $VPS_PROJECT_DIR"
echo ""
read -p "Voulez-vous continuer ? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Annulé."
    exit 1
fi

echo ""
echo "📤 Étape 1/5 : Copie des scripts de diagnostic..."
echo "════════════════════════════════════════════════════════════"

# Créer le répertoire scripts sur le VPS si nécessaire
ssh "$VPS_USER@$VPS_HOST" "mkdir -p /home/ubuntu/arome_tools"

# Copier les scripts de diagnostic
scp scripts/test_arome_availability.sh "$VPS_USER@$VPS_HOST:/home/ubuntu/arome_tools/" && echo -e "${GREEN}✓ test_arome_availability.sh copié${NC}" || echo -e "${RED}✗ Échec${NC}"
scp scripts/monitor_arome.sh "$VPS_USER@$VPS_HOST:/home/ubuntu/arome_tools/" && echo -e "${GREEN}✓ monitor_arome.sh copié${NC}" || echo -e "${RED}✗ Échec${NC}"

# Rendre exécutables
ssh "$VPS_USER@$VPS_HOST" "chmod +x /home/ubuntu/arome_tools/*.sh" && echo -e "${GREEN}✓ Permissions définies${NC}"

echo ""
echo "📤 Étape 2/5 : Copie du script principal corrigé..."
echo "════════════════════════════════════════════════════════════"

# Créer le répertoire backend/scripts si nécessaire
ssh "$VPS_USER@$VPS_HOST" "mkdir -p $VPS_PROJECT_DIR/backend/scripts"

# Copier le script corrigé avec un nom différent pour ne pas écraser l'ancien
scp scripts/arome_daily_pipeline_fixed.sh "$VPS_USER@$VPS_HOST:$VPS_PROJECT_DIR/backend/scripts/" && echo -e "${GREEN}✓ arome_daily_pipeline_fixed.sh copié${NC}" || echo -e "${RED}✗ Échec${NC}"

ssh "$VPS_USER@$VPS_HOST" "chmod +x $VPS_PROJECT_DIR/backend/scripts/arome_daily_pipeline_fixed.sh" && echo -e "${GREEN}✓ Permissions définies${NC}"

echo ""
echo "📤 Étape 3/5 : Copie de la documentation..."
echo "════════════════════════════════════════════════════════════"

scp scripts/README-AROME.md "$VPS_USER@$VPS_HOST:/home/ubuntu/arome_tools/" && echo -e "${GREEN}✓ README-AROME.md copié${NC}" || echo -e "${RED}✗ Échec${NC}"
scp docs/arome-vps-analysis.md "$VPS_USER@$VPS_HOST:/home/ubuntu/arome_tools/" && echo -e "${GREEN}✓ arome-vps-analysis.md copié${NC}" || echo -e "${RED}✗ Échec${NC}"

echo ""
echo "🔍 Étape 4/5 : Vérification des fichiers sur le VPS..."
echo "════════════════════════════════════════════════════════════"

ssh "$VPS_USER@$VPS_HOST" "ls -lh /home/ubuntu/arome_tools/"

echo ""
echo "✅ Étape 5/5 : Récapitulatif"
echo "════════════════════════════════════════════════════════════"
echo ""
echo "Fichiers déployés sur le VPS dans /home/ubuntu/arome_tools/ :"
echo "  • test_arome_availability.sh  - Test de disponibilité des données"
echo "  • monitor_arome.sh            - Monitoring du pipeline"
echo "  • README-AROME.md             - Guide complet"
echo "  • arome-vps-analysis.md       - Analyse détaillée"
echo ""
echo "Script corrigé déployé dans $VPS_PROJECT_DIR/backend/scripts/ :"
echo "  • arome_daily_pipeline_fixed.sh"
echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║              PROCHAINES ÉTAPES SUR LE VPS                 ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "Connectez-vous au VPS et exécutez :"
echo ""
echo "  ssh $VPS_USER@$VPS_HOST"
echo ""
echo "1. Diagnostic :"
echo "   cd /home/ubuntu/arome_tools"
echo "   bash test_arome_availability.sh"
echo "   bash monitor_arome.sh"
echo ""
echo "2. Si le diagnostic est OK, remplacer le script :"
echo "   cd $VPS_PROJECT_DIR/backend/scripts"
echo "   cp arome_daily_pipeline.sh arome_daily_pipeline.sh.backup_\$(date +%Y%m%d)"
echo "   cp arome_daily_pipeline_fixed.sh arome_daily_pipeline.sh"
echo ""
echo "3. Tester manuellement :"
echo "   bash arome_daily_pipeline.sh"
echo ""
echo "4. Consulter la doc complète :"
echo "   less /home/ubuntu/arome_tools/README-AROME.md"
echo ""
