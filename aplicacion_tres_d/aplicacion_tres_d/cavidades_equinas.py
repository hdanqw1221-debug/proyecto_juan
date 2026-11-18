"""Simulador 3D de las cavidades torácica, abdominal e inguinal del caballo.

Punto de entrada que lanza la aplicación Java (configuración "script3d": "cavidades_equinas.py").
El código del visor, los modelos y las fichas están en la carpeta `cavidades/`.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "cavidades"))

from visor.diagnostico import activar_faulthandler  # noqa: E402

activar_faulthandler("general")  # C08: pila de Python si el visor muere (registros/)

from visor.visor_cavidades import main  # noqa: E402

if __name__ == "__main__":
    main()
