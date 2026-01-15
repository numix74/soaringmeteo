#!/usr/bin/env python3
"""
Serveur Flask pour validation visuelle des outputs v1 vs v2
Focus sur clouds-rain et xc-flying-potential
"""

from flask import Flask, render_template_string, send_file, request
import os
from pathlib import Path

app = Flask(__name__)

# Configuration des chemins
V1_BASE = Path("/home/ubuntu/soaringmeteo/output/7/gfs")
V2_BASE = Path("/home/ubuntu/soaringmeteo/backend-v2/target/output-unified/7/gfs")

# Run à valider (le plus récent)
RUN_DATE = "2025-12-31T00"

# Mapping des noms de couches V2 -> V1
LAYER_MAPPING = {
    "xc-flying-potential": "xc-potential",  # V1 utilise un nom différent
    "clouds-rain": "clouds-rain"  # Même nom
}

# Couches à valider (nom V2)
LAYERS = ["clouds-rain", "xc-flying-potential"]

# Heures disponibles (V1 a moins d'heures que V2)
V1_HOURS = 47  # V1 run partiel
V2_HOURS = 118  # V2 run complet

HTML_TEMPLATE = """
<!DOCTYPE html>
<html>
<head>
    <title>Validation Visuelle - Backend v2</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            margin: 20px;
            background-color: #f0f0f0;
        }
        h1 {
            color: #333;
        }
        .controls {
            background: white;
            padding: 20px;
            border-radius: 8px;
            margin-bottom: 20px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .controls label {
            margin-right: 10px;
            font-weight: bold;
        }
        .controls select, .controls button {
            padding: 8px 12px;
            margin-right: 10px;
            font-size: 14px;
        }
        .comparison {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 20px;
            margin-bottom: 20px;
        }
        .version {
            background: white;
            padding: 15px;
            border-radius: 8px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .version h2 {
            margin-top: 0;
            color: #555;
        }
        .version img {
            width: 100%;
            border: 2px solid #ddd;
            border-radius: 4px;
            image-rendering: pixelated;  /* Pas d'interpolation floue */
            image-rendering: -moz-crisp-edges;
            image-rendering: crisp-edges;
        }
        .info {
            background: #e8f4f8;
            padding: 15px;
            border-radius: 8px;
            margin-bottom: 20px;
            border-left: 4px solid #2196F3;
        }
        .warning {
            background: #fff3cd;
            padding: 15px;
            border-radius: 8px;
            margin: 10px 0;
            border-left: 4px solid #ffc107;
            color: #856404;
        }
        .error {
            background: #ffebee;
            padding: 15px;
            border-radius: 8px;
            margin: 10px 0;
            border-left: 4px solid #f44336;
            color: #c62828;
        }
        .nav-buttons {
            margin-top: 10px;
        }
        .nav-buttons button {
            padding: 10px 20px;
            font-size: 16px;
            margin-right: 10px;
            cursor: pointer;
        }
        .status {
            display: inline-block;
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 12px;
            font-weight: bold;
        }
        .status.exists {
            background: #4caf50;
            color: white;
        }
        .status.missing {
            background: #f44336;
            color: white;
        }
    </style>
</head>
<body>
    <h1>🔍 Validation Visuelle Backend-v2</h1>

    <div class="info">
        <strong>Run:</strong> {{ run_date }}<br>
        <strong>Heure actuelle:</strong> {{ current_hour }}h<br>
        <strong>Couche actuelle:</strong> {{ current_layer }}<br>
        <strong>V1 disponible:</strong> 0-{{ v1_max_hour }}h ({{ v1_max_hour + 1 }} heures)<br>
        <strong>V2 disponible:</strong> 0-{{ v2_max_hour }}h ({{ v2_max_hour + 1 }} heures)
    </div>

    {% if current_hour >= v1_max_hour %}
    <div class="warning">
        ⚠️ <strong>Attention:</strong> L'heure {{ current_hour }}h n'est pas disponible dans V1 (max: {{ v1_max_hour }}h).
        Seule V2 est affichée.
    </div>
    {% endif %}

    <div class="controls">
        <form method="GET" action="/">
            <label>Couche:</label>
            <select name="layer" onchange="this.form.submit()">
                {% for layer in layers %}
                <option value="{{ layer }}" {% if layer == current_layer %}selected{% endif %}>
                    {{ layer }}
                </option>
                {% endfor %}
            </select>

            <label>Heure:</label>
            <select name="hour" onchange="this.form.submit()">
                {% for h in range(v2_max_hour + 1) %}
                <option value="{{ h }}" {% if h == current_hour %}selected{% endif %}>
                    {{ h }}h {% if h >= v1_max_hour %}(V2 seulement){% endif %}
                </option>
                {% endfor %}
            </select>

            <div class="nav-buttons">
                {% if current_hour > 0 %}
                <button type="submit" name="hour" value="{{ current_hour - 1 }}">← Heure précédente</button>
                {% endif %}
                {% if current_hour < v2_max_hour %}
                <button type="submit" name="hour" value="{{ current_hour + 1 }}">Heure suivante →</button>
                {% endif %}

                <button type="submit" name="hour" value="0">⏮ Début</button>
                <button type="submit" name="hour" value="{{ v1_max_hour }}">⏭ Fin V1</button>
            </div>
        </form>
    </div>

    {% if v1_error or v2_error %}
    <div class="error">
        {% if v1_error %}
        <strong>V1:</strong> {{ v1_error }}<br>
        {% endif %}
        {% if v2_error %}
        <strong>V2:</strong> {{ v2_error }}
        {% endif %}
    </div>
    {% endif %}

    <div class="comparison">
        <div class="version">
            <h2>V1 (Production)
                <span class="status {% if v1_exists %}exists{% else %}missing{% endif %}">
                    {% if v1_exists %}✓{% else %}✗{% endif %}
                </span>
            </h2>
            {% if v1_exists %}
            <img src="/image/v1/{{ current_layer }}/{{ current_hour }}" alt="V1">
            <p><small>{{ v1_path }}</small></p>
            {% else %}
            <p class="error">Image non trouvée ou heure non disponible (V1 max: {{ v1_max_hour }}h)</p>
            <p><small>{{ v1_path }}</small></p>
            {% endif %}
        </div>

        <div class="version">
            <h2>V2 (Backend-v2)
                <span class="status {% if v2_exists %}exists{% else %}missing{% endif %}">
                    {% if v2_exists %}✓{% else %}✗{% endif %}
                </span>
            </h2>
            {% if v2_exists %}
            <img src="/image/v2/{{ current_layer }}/{{ current_hour }}" alt="V2">
            <p><small>{{ v2_path }}</small></p>
            {% else %}
            <p class="error">Image non trouvée</p>
            <p><small>{{ v2_path }}</small></p>
            {% endif %}
        </div>
    </div>

    <div class="info">
        <strong>Instructions de validation:</strong>
        <ul>
            <li>✅ <strong>clouds-rain</strong> : Vérifier distribution des nuages et précipitations</li>
            <li>✅ <strong>xc-flying-potential</strong> : Vérifier le potentiel XC (gradient de couleurs)</li>
            <li>🔍 Comparez visuellement les deux images</li>
            <li>📍 Vérifiez l'alignement spatial (pas de décalage)</li>
            <li>🎨 Vérifiez les couleurs et intensités</li>
            <li>⏰ Naviguez entre les heures pour vérifier la cohérence temporelle</li>
            <li>⚠️ V1 n'a que {{ v1_max_hour + 1 }} heures, V2 en a {{ v2_max_hour + 1 }}</li>
        </ul>
    </div>
</body>
</html>
"""

@app.route('/')
def index():
    layer = request.args.get('layer', LAYERS[0])
    hour = int(request.args.get('hour', 0))

    # Mapping du nom de couche pour V1
    v1_layer_name = LAYER_MAPPING.get(layer, layer)

    # Chemins v1 et v2
    v1_path = V1_BASE / RUN_DATE / "pyrenees" / v1_layer_name / f"{hour}.png"
    v2_path = V2_BASE / RUN_DATE / "pyrenees" / layer / f"{hour}.png"

    v1_exists = v1_path.exists() and hour < V1_HOURS
    v2_exists = v2_path.exists()

    v1_error = None
    v2_error = None

    if not v1_exists:
        if hour >= V1_HOURS:
            v1_error = f"Heure {hour} non disponible (V1 max: {V1_HOURS-1}h)"
        else:
            v1_error = f"Fichier introuvable: {v1_path}"

    if not v2_exists:
        v2_error = f"Fichier introuvable: {v2_path}"

    return render_template_string(
        HTML_TEMPLATE,
        layers=LAYERS,
        current_layer=layer,
        current_hour=hour,
        run_date=RUN_DATE,
        v1_path=str(v1_path),
        v2_path=str(v2_path),
        v1_exists=v1_exists,
        v2_exists=v2_exists,
        v1_error=v1_error,
        v2_error=v2_error,
        v1_max_hour=V1_HOURS - 1,
        v2_max_hour=V2_HOURS - 1
    )

@app.route('/image/<version>/<layer>/<int:hour>')
def serve_image(version, layer, hour):
    # Mapping du nom de couche pour V1
    v1_layer_name = LAYER_MAPPING.get(layer, layer)

    if version == 'v1':
        path = V1_BASE / RUN_DATE / "pyrenees" / v1_layer_name / f"{hour}.png"
    else:  # v2
        path = V2_BASE / RUN_DATE / "pyrenees" / layer / f"{hour}.png"

    if not path.exists():
        return f"Image not found: {path}", 404

    return send_file(path, mimetype='image/png')

if __name__ == '__main__':
    print(f"🚀 Serveur de validation lancé sur http://51.38.221.186:5000/")
    print(f"📊 Run: {RUN_DATE}")
    print(f"🎨 Couches: {', '.join(LAYERS)}")
    print(f"⏰ V1: 0 à {V1_HOURS-1}h ({V1_HOURS} heures)")
    print(f"⏰ V2: 0 à {V2_HOURS-1}h ({V2_HOURS} heures)")
    print(f"📍 V1: {V1_BASE}")
    print(f"📍 V2: {V2_BASE}")
    app.run(host='0.0.0.0', port=5000, debug=True)
