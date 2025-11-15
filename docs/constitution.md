# Constitution SoaringMeteo

## Vision et Objectifs

SoaringMeteo vise à fournir des prévisions météorologiques avancées et personnalisées pour le vol libre (parapente, deltaplane, planeur), en intégrant et exploitant les meilleurs modèles disponibles (GFS, WRF, AROME, bientôt ICON/ECMWF) pour maximiser la sécurité, la performance et l'expérience des pilotes.

## Principes

- Précision : Offrir des prévisions de haute qualité - résolution 2km (WRF, AROME), jusqu'à 8 jours (GFS)
- Ouverture : Utiliser des sources de données publiques, favoriser la documentation ouverte et le partage
- Interopérabilité : Architecture modulaire, facile à étendre vers de nouveaux modèles, zones, produits
- Portabilité : Fonctionne sur serveurs dédiés et infra scalable, API et automatisation
- Explicabilité : Des diagrammes, visualisations, légendes et popups explicites pour chaque paramètre météo
- Robustesse : Système de logs, monitoring automatisé, backup et rollback documentés

## Contexte

- Backend principal en Scala, data pipelines pour chaque modèle
- Frontend SolidJS/TypeScript, cartographie interactive OpenLayers, PWA
- Traitement, visualisation et stockage (PNG/MVT/H2)
- Multiples modules métiers : calculs thermiques, vent, nuages, cloud cover, précipitation...
- Orchestration par scripts shell et jobs CRON
- Prise en compte des retours pilotes, UI/UX adaptée à la pratique

## Valeurs

- Service communautaire, engagé pour la sécurité et l'amélioration continue
- Adaptabilité, réactivité aux besoins de la communauté vol libre
- Qualité scientifique, rigueur sur le calcul, le format des données, la présentation
- Transparence sur les limites, les incertitudes et les évolutions prévues

## Gouvernance / Règles

- Architecture spécifique : chaque module ou modèle (GFS, AROME, WRF, etc) implémente le trait commun `MeteoData`
- Documentation et décisions (ADR) versionnées dans le dépôt, processus d'analyse et de refonte documentés
- Organisation des backups et des migrations lors des évolutions majeures
- Tests unitaires et end-to-end sur chaque adapter, pipeline, API publique
