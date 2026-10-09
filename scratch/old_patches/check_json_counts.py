import json
from pathlib import Path

base = Path(r"c:\Users\user\Desktop\proyecto_juan\aplicacion_tres_d\aplicacion_tres_d")
for name in ["sistema_cardiovascular.json", "sistema_muscular.json", "sistema_oseo.json", "sistema_organosinternos.json", "piel_caballo.json"]:
    p = base / name
    if p.exists():
        with open(p, "r", encoding="utf-8") as f:
            d = json.load(f)
            print(f"{name}: {len(d)} keys")
