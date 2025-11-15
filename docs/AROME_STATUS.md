# 🌪️ AROME Pays Basque - État du Système

**Date installation** : 16 octobre 2025  
**Statut** : ✅ OPÉRATIONNEL - Automatisation complète

---

## ✅ CE QUI FONCTIONNE (100%)

### Infrastructure
- ✅ AROME 0.025° (2.5 km) extraction Scala complète
- ✅ Lecture GRIB2 avec NetCDF Java
- ✅ Toutes données thermiques : PBLH, flux sensible/latent, CAPE, vent
- ✅ Calcul W* (vitesse thermique)

### Génération cartes
- ✅ 4 types : thermals, pblh, wind, cape
- ✅ 43 heures de prévision (0-42h)
- ✅ 172 cartes PNG générées automatiquement
- ✅ Légendes et échelles de couleurs

### Automatisation
- ✅ Téléchargement quotidien AROME 00Z à 05h30 UTC
- ✅ Génération cartes à 10h00 UTC
- ✅ Nettoyage automatique (garde 3 jours)
- ✅ Logs dans `/var/log/soaringmeteo/`

### Accès web
- ✅ Cartes publiées : http://51.254.207.208/arome/latest/maps/
- ✅ Lien symbolique `latest` → run le plus récent

---

## 📅 Planning Automatique

**Chaque jour :**
- 04h30 → GFS 00Z
- 05h30 → AROME 00Z téléchargement
- 10h00 → AROME génération cartes
- 11h00 → Monitoring santé
- 16h00 → GFS 12Z
- 19h00 → Monitoring santé

**Dimanche 02h00 :**
- Nettoyage GFS (>7 jours)
- Nettoyage AROME (>3 jours)

---

## 🔍 Vérification au retour
```bash
# 1. Vérifier logs récents
tail -100 /var/log/soaringmeteo/cron.log

# 2. Voir runs disponibles
ls -lht /usr/share/nginx/html/arome/ | head -5

# 3. Compter cartes dernière génération
ls /usr/share/nginx/html/arome/latest/maps/*.png | wc -l
# Attendu : 172 cartes

# 4. Tester une carte dans navigateur
# http://51.254.207.208/arome/latest/maps/thermals_h012.png

# 5. Vérifier cron actif
crontab -l
```

---

## 📂 Structure fichiers
```
/home/ubuntu/
├── download_arome_daily.sh      # Téléchargement quotidien
├── generate_arome_daily.sh      # Génération cartes
└── WRF_BUILD/WPS-4.5/DATA_AROME/
    ├── 20251016_00/            # Runs téléchargés
    └── ...

/usr/share/nginx/html/arome/
├── latest → 20251015_15/       # Lien vers dernière génération
├── 20251015_15/
│   └── maps/
│       ├── thermals_h000.png
│       ├── cape_h012.png
│       └── ... (172 cartes)
└── ...

/var/log/soaringmeteo/
├── cron.log                    # Logs automatisation
├── arome_download_*.log        # Logs téléchargement
└── arome_generate_*.log        # Logs génération
```

---

## 🚀 Prochaines étapes (TODO)

### Priorité HAUTE
- [ ] Intégration frontend React (3h)
  - Afficher cartes AROME dans interface web
  - Timeline interactive 0-42h
  - Sélection paramètre (thermals/wind/pblh/cape)

### Priorité MOYENNE
- [ ] Génération soundings par site (2h)
- [ ] API REST pour servir données (2h)
- [ ] Page comparaison GFS vs AROME (1h)

### Priorité BASSE
- [ ] Optimisation: lecture échéance spécifique sans charger toutes (forum)
- [ ] Combiner AROME 0.01° + 0.025° (si pertinent)

---

## 🐛 Debugging

### Si téléchargement échoue
```bash
# Tester manuellement
~/download_arome_daily.sh 00

# Vérifier disponibilité données
curl -I "https://object.files.data.gouv.fr/meteofrance-pnt/pnt/$(date -u +%Y-%m-%d)T00:00:00Z/arome/0025/SP1/arome__0025__SP1__00H06H__$(date -u +%Y-%m-%d)T00:00:00Z.grib2"
```

### Si génération échoue
```bash
# Tester manuellement
~/generate_arome_daily.sh

# Vérifier permissions
ls -ld /usr/share/nginx/html/arome/
```

### Si cartes pas visibles
```bash
# Vérifier nginx
sudo systemctl status nginx

# Vérifier fichiers
ls /usr/share/nginx/html/arome/latest/maps/ | head
```

---

## 📊 Ressources

**Utilisation quotidienne :**
- Téléchargement AROME : ~1 GB
- Cartes générées : ~11 MB
- Temps traitement : ~4-5 min

**Espace disque (3 jours) :**
- GRIB2 bruts : ~3 GB
- Cartes PNG : ~33 MB

**CPU/RAM :**
- Téléchargement : négligeable
- Génération : 100% CPU pendant 4 min, ~2 GB RAM

---

## 🎯 Performances

- **Résolution** : 2.5 km (101×33 points Pays Basque)
- **Prévision** : 0-42 heures
- **Mise à jour** : 1×/jour (00Z)
- **Latence publication** : Disponible vers 10h15 UTC

---

## 📞 Contacts & Ressources

**Documentation AROME :**
- https://donneespubliques.meteofrance.fr/

**Forum support :**
- https://forum.mmm.ucar.edu/ (WRF/WPS)
- Stack Overflow (NetCDF Java)

**Dépôt SoaringMeteo :**
- ~/soaringmeteo/ (backend Scala + frontend React)

---

**✅ Système prêt pour production !**

