  📊 Rapport de Vérification - Pipeline AROME

  ✅ CE QUI FONCTIONNE

  1. Téléchargement des fichiers GRIB ✅

  Fichier : /home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh

  - Source : Météo-France (object.files.data.gouv.fr)
  - Fréquence : Run 06h UTC quotidien (cron à 10h UTC)
  - Fichiers téléchargés : 12 fichiers GRIB groupés
    - SP1 (Surface Package 1) : Température, Vent
    - SP2 (Surface Package 2) : CAPE, PBLH, Nuages
    - SP3 (Surface Package 3) : Flux chaleur, Radiation
    - Groupes : 00H06H, 07H12H, 13H18H, 19H24H
  - Destination : /mnt/soaringmeteo-data/arome/grib/pays_basque/
  - Statut dernier run :

  # Logs récents
  ls -lh /home/ubuntu/soaringmeteo/backend/logs/arome_*.log

  2. Transformation et extraction des données ✅

  Fichier : /home/ubuntu/soaringmeteo/backend/arome/src/main/scala/org/soaringmeteo/arome/Main.scala

  - Traitement : Lecture GRIB via AromeGrib.fromGroupFiles()
  - Extraction : Par timestep 2D (optimisé) au lieu de 4D complet
  - Performance : ~45-60 secondes pour 25 heures × 8,601 points
  - Zone : Pays Basque (42.8°N-43.6°N, -2.0°W-0.5°W, 141×61 points)

  3. Génération des cartes PNG ✅

  Code : org.soaringmeteo.out.Raster.writeAllPngFiles()

  - Sortie : /mnt/soaringmeteo-data/arome/output/pays_basque/maps/HH/
  - Paramètres générés (13 types):
    - boundary-layer-depth/ - Hauteur couche limite
    - thermal-velocity/ - Vitesse thermiques
    - clouds-rain/ - Nuages et précipitations
    - cumulus-depth/ - Profondeur cumulus
    - soaring-layer-depth/ - Épaisseur couche ascendante
    - xc-potential/ - Potentiel cross-country

  Vérification :
  ls -la /mnt/soaringmeteo-data/arome/output/pays_basque/maps/00/thermal-velocity/
  # -rw-rw-r--  1 ubuntu ubuntu  183 nov.  12 10:00 0.png

  4. Génération des tuiles vectorielles MVT ✅

  Code : org.soaringmeteo.out.VectorTiles.writeAllVectorTiles()

  - Sortie : /mnt/soaringmeteo-data/arome/output/pays_basque/maps/HH/wind-*/zoom/
  - Paramètres avec MVT (7 types de vent):
    - wind-surface/
    - wind-300m-agl/
    - wind-boundary-layer/
    - wind-soaring-layer-top/
    - wind-2000m-amsl/
    - wind-3000m-amsl/
    - wind-4000m-amsl/

  Vérification :
  ls -la /mnt/soaringmeteo-data/arome/output/pays_basque/maps/00/wind-surface/0/
  # 180 fichiers .mvt générés (différents niveaux de zoom)

  ---
  ❌ CE QUI MANQUE

  1. Génération des fichiers JSON de prévisions ❌

  Problème : Le frontend s'attend à des fichiers JSON par localisation (comme GFS)

  GFS génère :
  /mnt/soaringmeteo-data/gfs/output/7/gfs/2025-11-13T00/pyrenees/locations/0-0.json

  AROME ne génère PAS :
  find /mnt/soaringmeteo-data/arome/ -name "*.json"
  # (aucun résultat)

  Code manquant : L'équivalent de la sauvegarde en JSON clustérisé que GFS fait

  2. Configuration NGINX pour servir AROME ❌

  Configuration actuelle (/etc/nginx/sites-available/soaringmeteo-unified) :

  # Sert uniquement GFS
  location ~ ^/v2/data/(.*)$ {
      alias /mnt/soaringmeteo-data/gfs/output/$1;
      expires 1h;
  }

  Configuration manquante pour AROME : Pas de route pour accéder aux données AROME

  3. Métadonnées forecast.json ❌

  GFS a :
  /mnt/soaringmeteo-data/gfs/output/7/gfs/forecast.json

  AROME n'a pas : Fichier de métadonnées listant les runs disponibles

  ---
  🔧 ACTIONS À ENTREPRENDRE

  Action 1 : Générer les fichiers JSON de prévisions par localisation

  Modifier : /home/ubuntu/soaringmeteo/backend/arome/src/main/scala/org/soaringmeteo/arome/Main.scala

  Ajouter après la ligne 135 (après Store.save()) :

  // Sauvegarder les prévisions en JSON pour le frontend
  logger.debug(s"    Writing location forecasts JSON...")
  org.soaringmeteo.out.LocationForecasts.write(
    outputBaseDir / "7" / "arome" / initTime.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME).split("T")(0) + "T06" / setting.name,
    setting.zone,
    hour,
    meteoData
  )

  Action 2 : Générer forecast.json

  Créer une nouvelle fonction dans Main.scala :

  def writeForecastMetadata(outputBaseDir: os.Path, initTime: OffsetDateTime, zones: Seq[String]): Unit = {
    val forecastMeta = ujson.Obj(
      "zones" -> zones.map(z => ujson.Obj("id" -> z, "name" -> z)),
      "forecasts" -> ujson.Arr(
        ujson.Obj(
          "path" -> s"${initTime.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME).split("T")(0)}T06",
          "init" -> initTime.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
          "latest" -> 24,
          "zones" -> zones
        )
      )
    )

    os.write.over(outputBaseDir / "7" / "arome" / "forecast.json", ujson.write(forecastMeta, indent = 2))
  }

  Action 3 : Modifier la configuration NGINX

  Fichier : /etc/nginx/sites-available/soaringmeteo-unified

  Ajouter ces locations :

  # Données AROME
  location ~ ^/v2/data/7/arome/(.*)$ {
      alias /mnt/soaringmeteo-data/arome/output/7/arome/$1;
      expires 1h;
      add_header Access-Control-Allow-Origin *;
  }

  # Pas de cache pour forecast.json AROME
  location ~ /v2/data/7/arome/forecast.json$ {
      alias /mnt/soaringmeteo-data/arome/output/7/arome/forecast.json;
      expires -1;
      add_header Cache-Control "no-store, no-cache, must-revalidate";
      add_header Access-Control-Allow-Origin *;
  }

  # Tuiles PNG AROME
  location ~ ^/v2/data/arome-tiles/(.*)$ {
      alias /mnt/soaringmeteo-data/arome/output/pays_basque/maps/$1;
      expires 1h;
      add_header Access-Control-Allow-Origin *;
  }

  Puis recharger NGINX :
  sudo nginx -t
  sudo systemctl reload nginx

  Action 4 : Mettre à jour le frontend pour supporter AROME

  Fichier : /home/ubuntu/soaringmeteo/frontend/src/data/Model.ts

  Ajouter AROME aux modèles disponibles :

  export const models = [
    { id: "gfs", name: "GFS", path: "gfs" },
    { id: "arome", name: "AROME Pays Basque", path: "arome" }  // NOUVEAU
  ]

  ---
  📈 Structure cible finale

  /mnt/soaringmeteo-data/arome/output/
  └── 7/
      └── arome/
          ├── forecast.json                    # Métadonnées (À CRÉER)
          └── 2025-11-15T06/                  # Run date
              └── pays_basque/
                  ├── locations/               # Prévisions par point (À CRÉER)
                  │   ├── 0-0.json
                  │   ├── 0-1.json
                  │   └── ...
                  └── maps/                    # Cartes existantes ✅
                      ├── 00/
                      │   ├── thermal-velocity/
                      │   │   └── 0.png
                      │   └── wind-surface/
                      │       └── 0/
                      │           └── *.mvt
                      └── ...

  ---
  🧪 Tests de vérification

  # 1. Vérifier dernier pipeline
  tail -100 /home/ubuntu/soaringmeteo/backend/logs/arome_*.log | grep -E "✓|✗|📊|✅|❌"

  # 2. Compter les cartes générées
  find /mnt/soaringmeteo-data/arome/output/pays_basque/maps/ -name "*.png" | wc -l
  find /mnt/soaringmeteo-data/arome/output/pays_basque/maps/ -name "*.mvt" | wc -l

  # 3. Tester accès NGINX (après config)
  curl -I http://51.254.207.208/v2/data/7/arome/forecast.json

  # 4. Vérifier espace disque
  du -sh /mnt/soaringmeteo-data/arome/

  ---
  ✅ Checklist d'implémentation

  - Téléchargement GRIB automatique
  - Traitement Scala + extraction données
  - Génération cartes PNG
  - Génération tuiles MVT
  - Génération fichiers JSON locations
  - Génération forecast.json
  - Configuration NGINX
  - Intégration frontend

  Progression actuelle : 60% complete

  Veux-tu que je procède à l'implémentation des parties manquantes ?

> procède à l'implémentation des parties manquantes

● Je vais implémenter les parties manquantes pour finaliser l'intégration AROME dans le frontend.
  ⎿  Session limit reached ∙ resets 3am
     /upgrade to increase your usage limit.

> reprend
  ⎿  Session limit reached ∙ resets 3am
     /upgrade to increase your usage limit.

> /exit
  ⎿  Goodbye!

  Todos
  ☐ Ajouter génération des fichiers JSON locations dans Main.scala
  ☐ Ajouter génération de forecast.json
  ☐ Configurer NGINX pour servir AROME
  ☐ Mettre à jour le frontend pour supporter AROME
  ☐ Tester l'intégration complète

