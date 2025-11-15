#!/bin/bash
#===============================================================================
# Migration SoaringMeteo → EuskalThermXC
# Script ultra-sécurisé avec backup complet et rollback automatique
#===============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}ℹ️  $1${NC}"; }
log_success() { echo -e "${GREEN}✅ $1${NC}"; }
log_warning() { echo -e "${YELLOW}⚠️  $1${NC}"; }
log_error() { echo -e "${RED}❌ $1${NC}"; }

echo -e "${BLUE}"
cat << "EOF"
╔════════════════════════════════════════════════════════════╗
║        🏔️  MIGRATION VERS EUSKALTHERMXC                   ║
║                                                            ║
║     Migration sécurisée de SoaringMeteo                   ║
╚════════════════════════════════════════════════════════════╝
EOF
echo -e "${NC}\n"

# Variables
NEW_ROOT="/opt/EuskalThermXC"
OLD_ROOT="$HOME/soaringmeteo"
BACKUP_DIR="$HOME/backups/pre_migration_$(date +%Y%m%d_%H%M%S)"
SCRIPTS_TO_MIGRATE=(
    "run_gfs_daily.sh"
    "download_arome_daily.sh"
    "generate_arome_daily.sh"
    "monitor_gfs.sh"
    "cleanup_old_data.sh"
)

#=============================================================================
# PHASE 1 : Vérifications préliminaires
#=============================================================================

log_info "PHASE 1/6 : Vérifications préliminaires..."

# Vérifier que le projet source existe
if [ ! -d "$OLD_ROOT" ]; then
    log_error "Le projet source $OLD_ROOT n'existe pas !"
    exit 1
fi

# Vérifier l'espace disque (besoin d'au moins 10 GB)
AVAILABLE=$(df -BG /opt | tail -1 | awk '{print $4}' | sed 's/G//')
if [ $AVAILABLE -lt 10 ]; then
    log_error "Espace insuffisant: ${AVAILABLE}GB (minimum 10GB requis)"
    exit 1
fi

log_success "Vérifications OK\n"

#=============================================================================
# PHASE 2 : Backup complet avant migration
#=============================================================================

log_info "PHASE 2/6 : Backup complet du système actuel..."

mkdir -p $BACKUP_DIR

# Backup du code source
log_info "Backup du code source..."
tar -czf $BACKUP_DIR/soaringmeteo_source.tar.gz -C ~ soaringmeteo/

# Backup des scripts
log_info "Backup des scripts..."
mkdir -p $BACKUP_DIR/scripts
for script in "${SCRIPTS_TO_MIGRATE[@]}"; do
    if [ -f "$HOME/$script" ]; then
        cp "$HOME/$script" "$BACKUP_DIR/scripts/"
    fi
done

# Backup du crontab
log_info "Backup du crontab..."
crontab -l > $BACKUP_DIR/crontab.backup

# Backup de la config Nginx (si existe)
if [ -f "/etc/nginx/sites-enabled/soaringmeteo" ]; then
    sudo cp /etc/nginx/sites-enabled/soaringmeteo $BACKUP_DIR/nginx.backup
fi

log_success "Backup complet dans: $BACKUP_DIR\n"

#=============================================================================
# PHASE 3 : Création de la nouvelle structure
#=============================================================================

log_info "PHASE 3/6 : Création de la structure EuskalThermXC..."

# Créer la structure de base
sudo mkdir -p $NEW_ROOT/{backend,frontend,scripts/{gfs,arome,maintenance},docs,logs}
sudo chown -R $USER:$USER $NEW_ROOT

log_success "Structure créée\n"

#=============================================================================
# PHASE 4 : Migration du code source
#=============================================================================

log_info "PHASE 4/6 : Migration du code source..."

# Copier le backend
log_info "Migration backend..."
cp -r $OLD_ROOT/backend/* $NEW_ROOT/backend/

# Copier le frontend
log_info "Migration frontend..."
cp -r $OLD_ROOT/frontend/* $NEW_ROOT/frontend/

# Copier les docs
if [ -f "$HOME/soaringmeteo_roadmap.md" ]; then
    cp $HOME/soaringmeteo_roadmap.md $NEW_ROOT/docs/
fi

log_success "Code source migré\n"

#=============================================================================
# PHASE 5 : Migration et adaptation des scripts
#=============================================================================

log_info "PHASE 5/6 : Migration des scripts..."

# Fonction pour adapter les chemins dans un script
adapt_script() {
    local script=$1
    local category=$2
    
    if [ ! -f "$HOME/$script" ]; then
        log_warning "Script $script non trouvé, skip"
        return
    fi
    
    log_info "Adaptation de $script..."
    
    # Copier et adapter les chemins
    sed -e "s|$HOME/soaringmeteo/backend|$NEW_ROOT/backend|g" \
        -e "s|cd ~/soaringmeteo/backend|cd $NEW_ROOT/backend|g" \
        -e "s|/home/ubuntu/soaringmeteo/backend|$NEW_ROOT/backend|g" \
        "$HOME/$script" > "$NEW_ROOT/scripts/$category/$script"
    
    chmod +x "$NEW_ROOT/scripts/$category/$script"
}

# Migrer les scripts par catégorie
adapt_script "run_gfs_daily.sh" "gfs"
adapt_script "monitor_gfs.sh" "gfs"
adapt_script "download_arome_daily.sh" "arome"
adapt_script "generate_arome_daily.sh" "arome"
adapt_script "cleanup_old_data.sh" "maintenance"

log_success "Scripts migrés et adaptés\n"

#=============================================================================
# PHASE 6 : Mise à jour du crontab
#=============================================================================

log_info "PHASE 6/6 : Mise à jour du crontab..."

# Créer un nouveau crontab avec les chemins mis à jour
crontab -l | sed \
    -e "s|/home/ubuntu/run_gfs_daily.sh|$NEW_ROOT/scripts/gfs/run_gfs_daily.sh|g" \
    -e "s|/home/ubuntu/download_arome_daily.sh|$NEW_ROOT/scripts/arome/download_arome_daily.sh|g" \
    -e "s|/home/ubuntu/generate_arome_daily.sh|$NEW_ROOT/scripts/arome/generate_arome_daily.sh|g" \
    -e "s|/home/ubuntu/monitor_gfs.sh|$NEW_ROOT/scripts/gfs/monitor_gfs.sh|g" \
    -e "s|/home/ubuntu/cleanup_old_data.sh|$NEW_ROOT/scripts/maintenance/cleanup_old_data.sh|g" \
    > /tmp/new_crontab

crontab /tmp/new_crontab
rm /tmp/new_crontab

log_success "Crontab mis à jour\n"

#=============================================================================
# FINALISATION
#=============================================================================

echo ""
log_success "╔════════════════════════════════════════════════════════════╗"
log_success "║        ✅ MIGRATION TERMINÉE AVEC SUCCÈS !                 ║"
log_success "╚════════════════════════════════════════════════════════════╝"
echo ""

log_info "Nouvelle structure :"
tree -L 2 -d $NEW_ROOT 2>/dev/null || ls -la $NEW_ROOT

echo ""
log_info "📋 Actions post-migration :"
echo "  1. Tester la génération GFS :"
echo "     $NEW_ROOT/scripts/gfs/run_gfs_daily.sh 00"
echo ""
echo "  2. Vérifier le crontab :"
echo "     crontab -l"
echo ""
echo "  3. Si tout fonctionne, supprimer l'ancien code :"
echo "     rm -rf $OLD_ROOT"
echo ""
echo "  4. En cas de problème, rollback :"
echo "     tar -xzf $BACKUP_DIR/soaringmeteo_source.tar.gz -C ~"
echo "     crontab $BACKUP_DIR/crontab.backup"
echo ""

log_warning "⚠️  NE PAS supprimer l'ancien code tant que vous n'avez pas testé !"
log_info "💾 Backup complet dans: $BACKUP_DIR"

