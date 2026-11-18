"""Simulador 3D del equino: Cavidad torácica (modelo inmersivo).

Desde lejos se ve la cavidad completa; al acercarse con la rueda del ratón se separa en sus
componentes. Clic en cualquier pieza para ver su ficha anatómica completa.
Punto de entrada para la aplicación Java ("script3d": "cavidad_toracica.py").
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "cavidades"))

from visor.diagnostico import activar_faulthandler  # noqa: E402

activar_faulthandler("toracica")  # C08: pila de Python si el visor muere (registros/)

from visor.visor_cavidades import main  # noqa: E402

if __name__ == "__main__":
    main("toracica")
