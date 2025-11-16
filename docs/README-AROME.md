# Scripts AROME - Guide d'Utilisation

Ce dossier contient les scripts pour diagnostiquer et corriger les problèmes du pipeline AROME sur votre VPS.

## 📋 Vue d'Ensemble

Le pipeline AROME télécharge les données météorologiques AROME depuis Météo-France et génère des cartes de prévisions pour le Pays Basque.

## 📁 Fichiers Fournis

### 1. `test_arome_availability.sh`
**Objectif:** Tester la disponibilité des données AROME et identifier le meilleur run à utiliser

**Utilisation sur le VPS:**
```bash
# Copier le script sur le VPS
scp scripts/test_arome_availability.sh ubuntu@VOTRE_VPS:/home/ubuntu/

# Se connecter au VPS
ssh ubuntu@VOTRE_VPS

# Rendre exécutable
chmod +x /home/ubuntu/test_arome_availability.sh

# Exécuter
bash /home/ubuntu/test_arome_availability.sh
```

**Ce script va:**
- ✅ Tester les runs 06Z, 00Z, 18Z et 12Z
- ✅ Vérifier la disponibilité des 12 fichiers GRIB
- ✅ Tester la vitesse de téléchargement
- ✅ Recommander le meilleur run à utiliser
- ✅ Estimer le temps de téléchargement total

**Sortie attendue:**
```
╔════════════════════════════════════════════════════════════╗
║     🔍 Test de Disponibilité AROME - 10:30 UTC             ║
╚════════════════════════════════════════════════════════════╝

Test 1: Run d'aujourd'hui 06Z
   Répertoire SP1: ✓ Accessible
   Fichier test (SP1_00H06H): ✓ Disponible (45 MB)
   Test des 12 fichiers GRIB:
   ............
   Résultat: 12/12 fichiers disponibles
   ✅ RUN COMPLET ET UTILISABLE

╔════════════════════════════════════════════════════════════╗
║                    ✅ RECOMMANDATION                       ║
╚════════════════════════════════════════════════════════════╝

Run à utiliser: 2025-11-11 06Z
```

### 2. `arome_daily_pipeline_fixed.sh`
**Objectif:** Version corrigée du script principal avec détection automatique du run et gestion d'erreurs améliorée

**Améliorations par rapport au script actuel:**
- ✅ Détection automatique du meilleur run disponible
- ✅ Retry avec backoff exponentiel
- ✅ Vérification d'intégrité des fichiers GRIB
- ✅ Logs détaillés et structurés
- ✅ Gestion d'erreurs robuste
- ✅ Nettoyage automatique

**Installation sur le VPS:**
```bash
# Copier le script
scp scripts/arome_daily_pipeline_fixed.sh ubuntu@VOTRE_VPS:/home/ubuntu/soaringmeteo/backend/scripts/

# Se connecter au VPS
ssh ubuntu@VOTRE_VPS

# Sauvegarder l'ancien script
cd /home/ubuntu/soaringmeteo/backend/scripts/
cp arome_daily_pipeline.sh arome_daily_pipeline.sh.backup_$(date +%Y%m%d)

# Remplacer par la nouvelle version
mv arome_daily_pipeline_fixed.sh arome_daily_pipeline.sh
chmod +x arome_daily_pipeline.sh
```

**Test manuel:**
```bash
# Exécuter manuellement pour tester
bash /home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh

# Suivre les logs en temps réel
tail -f /home/ubuntu/soaringmeteo/backend/logs/arome_*.log
```

### 3. `monitor_arome.sh`
**Objectif:** Vérifier l'état du pipeline AROME et identifier les problèmes

**Utilisation:**
```bash
# Copier sur le VPS
scp scripts/monitor_arome.sh ubuntu@VOTRE_VPS:/home/ubuntu/

# Rendre exécutable
ssh ubuntu@VOTRE_VPS "chmod +x /home/ubuntu/monitor_arome.sh"

# Exécuter
ssh ubuntu@VOTRE_VPS "bash /home/ubuntu/monitor_arome.sh"
```

**Informations fournies:**
- 📅 Dernière exécution du pipeline (succès/échec)
- 📦 État des fichiers GRIB téléchargés
- 🗺️ Nombre de cartes générées
- 💾 Espace disque disponible
- ⏰ Prochaine exécution cron
- 💡 Recommandations pour résoudre les problèmes

**Sortie attendue:**
```
╔════════════════════════════════════════════════════════════╗
║         🔍 Monitoring AROME Pays Basque                   ║
╚════════════════════════════════════════════════════════════╝

📅 Dernière exécution du pipeline:
  Fichier: arome_20251111_1000.log
  Date: 2025-11-11 10:00:15
  Taille: 45K
  Statut: ✅ SUCCÈS
  Run: 2025-11-11 06Z
  GRIB téléchargés: 12/12
  Cartes générées: 325 fichiers
  ✓ Dernière exécution il y a 2h

📦 Fichiers GRIB disponibles:
  Nombre de fichiers: 12/12
  Taille totale: 540 MB
  Plus récent: SP3_19H24H.grib2 (2h)
  ✅ Complet (12/12)

🗺️  Cartes générées:
  PNG: 150 fichiers
  MVT: 175 fichiers
  Total: 325 fichiers
  Heures de prévision: 25 répertoires
  Taille totale: 1.2G
  Plus récente: 2h
  ✅ Prévisions complètes (25h)

💡 Recommandations:
  ✅ Aucun problème détecté
```

## 🔧 Procédure de Diagnostic et Réparation

### Étape 1: Diagnostic Initial

```bash
# 1. Vérifier la disponibilité des données AROME
bash /home/ubuntu/test_arome_availability.sh

# 2. Vérifier l'état actuel du système
bash /home/ubuntu/monitor_arome.sh

# 3. Consulter les logs récents
tail -100 /home/ubuntu/soaringmeteo/backend/logs/arome_*.log | less
```

### Étape 2: Identifier le Problème

#### Problème A: Aucune donnée disponible
**Symptôme:** Le test montre "❌ AUCUN RUN DISPONIBLE"

**Solutions:**
- Vérifier la connexion internet: `ping data.gouv.fr`
- Vérifier l'URL de l'API: `curl -I https://object.files.data.gouv.fr/meteofrance-pnt/pnt/`
- Attendre quelques heures (les données peuvent avoir du retard)

#### Problème B: Téléchargement échoue
**Symptôme:** Le script affiche "✗ ÉCHEC" pour tous les fichiers

**Solutions:**
1. Vérifier que wget est installé: `which wget`
2. Tester manuellement un téléchargement:
   ```bash
   wget -S https://object.files.data.gouv.fr/meteofrance-pnt/pnt/2025-11-11T06:00:00Z/arome/0025/SP1/arome__0025__SP1__00H06H__2025-11-11T06:00:00Z.grib2
   ```
3. Vérifier les permissions du répertoire de destination:
   ```bash
   ls -la /mnt/soaringmeteo-data/arome/grib/pays_basque/
   ```

#### Problème C: Traitement Scala échoue
**Symptôme:** Téléchargement OK mais "❌ ERREUR traitement"

**Solutions:**
1. Vérifier que SBT est installé:
   ```bash
   sbt --version
   ```
2. Vérifier le fichier de configuration:
   ```bash
   cat /home/ubuntu/soaringmeteo/backend/pays_basque.conf
   ```
3. Compiler le projet manuellement:
   ```bash
   cd /home/ubuntu/soaringmeteo/backend
   sbt arome/compile
   ```
4. Tester avec des données existantes:
   ```bash
   cd /home/ubuntu/soaringmeteo/backend
   sbt "arome/run pays_basque.conf"
   ```

#### Problème D: Ancien script s'exécute
**Symptôme:** Les logs ne correspondent pas au nouveau script

**Solutions:**
1. Vérifier quel script est appelé par le cron:
   ```bash
   crontab -l | grep arome
   ```
2. Vérifier le contenu du script:
   ```bash
   head -30 /home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh
   ```
3. Comparer avec la version fournie:
   ```bash
   diff /home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh \
        /home/ubuntu/arome_daily_pipeline_fixed.sh
   ```

### Étape 3: Appliquer les Corrections

#### A. Mettre à jour le script principal

```bash
# Se connecter au VPS
ssh ubuntu@VOTRE_VPS

# Sauvegarder l'ancien script
cd /home/ubuntu/soaringmeteo/backend/scripts/
cp arome_daily_pipeline.sh arome_daily_pipeline.sh.backup_$(date +%Y%m%d_%H%M)

# Copier la nouvelle version (depuis votre machine locale)
# Sur votre machine locale:
scp scripts/arome_daily_pipeline_fixed.sh ubuntu@VOTRE_VPS:/home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh

# Sur le VPS, rendre exécutable:
chmod +x /home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh
```

#### B. Nettoyer le crontab

```bash
# Sauvegarder le cron actuel
crontab -l > ~/crontab_backup_$(date +%Y%m%d).txt

# Éditer le cron
crontab -e

# Supprimer toutes les lignes concernant AROME et garder uniquement:
# AROME Pays Basque - Pipeline complet quotidien
0 10 * * * /home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh >> /var/log/soaringmeteo/cron.log 2>&1

# Nettoyage hebdomadaire (dimanche 02h)
0 2 * * 0 find /mnt/soaringmeteo-data/arome/output/pays_basque/maps/ -type f -mtime +7 -delete 2>/dev/null
0 2 * * 0 find /home/ubuntu/soaringmeteo/backend/logs/ -name "arome_*.log" -mtime +30 -delete 2>/dev/null
```

#### C. Tester manuellement

```bash
# Exécuter le nouveau script
bash -x /home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh

# Observer les logs en temps réel (dans un autre terminal)
ssh ubuntu@VOTRE_VPS
tail -f /home/ubuntu/soaringmeteo/backend/logs/arome_*.log
```

#### D. Vérifier le résultat

```bash
# Attendre la fin de l'exécution puis vérifier
bash /home/ubuntu/monitor_arome.sh

# Vérifier les cartes générées
ls -lh /mnt/soaringmeteo-data/arome/output/pays_basque/maps/00/
ls -lh /mnt/soaringmeteo-data/arome/output/pays_basque/maps/24/
```

### Étape 4: Monitoring Continu

```bash
# Ajouter le monitoring au cron (2x par jour)
crontab -e

# Ajouter:
0 11,19 * * * /home/ubuntu/monitor_arome.sh >> /var/log/soaringmeteo/monitoring.log 2>&1
```

## 📊 Fichiers de Configuration

### Configuration Scala: `pays_basque.conf`

Vérifier que ce fichier existe et contient:

```hocon
include "reference.conf"

arome {
  zones = [
    {
      name = "Pays Basque"
      lon-min = -2.0
      lon-max = 0.5
      lat-min = 42.8
      lat-max = 43.6
      step = 0.025
      grib-directory = "/mnt/soaringmeteo-data/arome/grib/pays_basque"
      output-directory = "/mnt/soaringmeteo-data/arome/output/pays_basque"
    }
  ]
}

h2db {
  url = "jdbc:h2:file:/mnt/soaringmeteo-data/arome/arome.h2"
  driver = "org.h2.Driver"
}
```

Si le fichier n'existe pas:
```bash
cat > /home/ubuntu/soaringmeteo/backend/pays_basque.conf << 'EOF'
include "reference.conf"

arome {
  zones = [
    {
      name = "Pays Basque"
      lon-min = -2.0
      lon-max = 0.5
      lat-min = 42.8
      lat-max = 43.6
      step = 0.025
      grib-directory = "/mnt/soaringmeteo-data/arome/grib/pays_basque"
      output-directory = "/mnt/soaringmeteo-data/arome/output/pays_basque"
    }
  ]
}

h2db {
  url = "jdbc:h2:file:/mnt/soaringmeteo-data/arome/arome.h2"
  driver = "org.h2.Driver"
}
EOF
```

## 🚨 Dépannage Rapide

### Problème: "Permission denied"
```bash
# Donner les bonnes permissions
sudo chown -R ubuntu:ubuntu /home/ubuntu/soaringmeteo/
sudo chown -R ubuntu:ubuntu /mnt/soaringmeteo-data/arome/
chmod +x /home/ubuntu/soaringmeteo/backend/scripts/*.sh
```

### Problème: "SBT not found"
```bash
# Installer SBT
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt-get update
sudo apt-get install sbt
```

### Problème: "Out of memory"
```bash
# Augmenter la mémoire JVM dans le script
# Éditer le script et chercher "sbt"
# Ajouter avant: export SBT_OPTS="-Xmx4G -Xms2G"
```

### Problème: "wgrib2 not found"
```bash
# Installer wgrib2
sudo apt-get update
sudo apt-get install wgrib2
```

## 📞 Support

Si vous rencontrez toujours des problèmes après avoir suivi ces étapes:

1. **Collecter les informations de diagnostic:**
   ```bash
   # Sur le VPS, créer un rapport complet
   {
     echo "=== Monitoring AROME ==="
     bash /home/ubuntu/monitor_arome.sh
     echo ""
     echo "=== Test de Disponibilité ==="
     bash /home/ubuntu/test_arome_availability.sh
     echo ""
     echo "=== Crontab ==="
     crontab -l
     echo ""
     echo "=== Dernier Log ==="
     tail -100 /home/ubuntu/soaringmeteo/backend/logs/arome_*.log
   } > /tmp/arome_diagnostic_$(date +%Y%m%d_%H%M).txt

   # Télécharger le rapport
   scp ubuntu@VOTRE_VPS:/tmp/arome_diagnostic_*.txt .
   ```

2. **Analyser le rapport** et consulter la documentation AROME

## 📝 Notes Importantes

- **Timing:** Les données AROME 06Z sont généralement disponibles vers 09h-10h UTC
- **Taille:** Chaque run complet fait environ 500 MB (12 fichiers GRIB)
- **Durée:** Le téléchargement + traitement prend environ 15-30 minutes
- **Rétention:** Les anciennes données sont nettoyées automatiquement après 7 jours
- **Logs:** Conservés pendant 30 jours

## ✅ Checklist de Mise en Production

- [ ] Scripts copiés sur le VPS
- [ ] Permissions correctes (chmod +x)
- [ ] Test de disponibilité réussi
- [ ] Test manuel du pipeline réussi
- [ ] Crontab nettoyé et mis à jour
- [ ] Monitoring fonctionnel
- [ ] Vérification des cartes générées
- [ ] Sauvegarde de l'ancienne configuration
- [ ] Documentation à jour

---

**Version:** 1.0
**Date:** 11 novembre 2025
**Auteur:** Claude Code Analysis
