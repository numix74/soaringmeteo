# 🌪️ À faire au retour - AROME Pays Basque

## ✅ Ce qui fonctionne PARFAITEMENT
- Téléchargement quotidien AROME (05h30)
- Génération cartes (10h00) 
- 172 cartes PNG accessibles : http://51.254.207.208/arome/latest/maps/thermals_h012.png
- Backend 100% opérationnel

## 🔧 À débugger (10 min)
Frontend freeze au clic. Probable cause : zone coordinates invalides.

**Solution** :
1. Ajuster extent dans ForecastMetadata.ts ligne ~180
2. Ou simplifier : désactiver temporairement AROME dans frontend
3. Recompiler : `cd ~/soaringmeteo/frontend && npm run build`

## 📊 Logs à vérifier
```bash
tail -100 /var/log/soaringmeteo/cron.log
ls /arome/latest/maps/ | wc -l  # doit être 172
```

Bon voyage ! 🎉
