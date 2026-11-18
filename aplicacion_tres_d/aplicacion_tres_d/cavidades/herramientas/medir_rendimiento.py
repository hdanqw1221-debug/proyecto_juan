"""Rendimiento del visor (Bloque 2, PF-29): carga hasta el primer cuadro, fps en una rotación de 360° y latencia de selección.

Cada repetición se ejecuta en un proceso aparte (arranque en frío, como lo vive el usuario).
Uso: python medir_rendimiento.py --repeticiones 5 --csv salida.csv [--modelos toracica abdominal inguinal general]
"""
import argparse
import csv
import json
import os
import statistics
import subprocess
import sys
import time
from pathlib import Path

AQUI = Path(__file__).resolve()
CAVIDADES = AQUI.parents[1]
PASOS_GIRO = 72        # 72 pasos de 5° = 360°
CLICS = 20


def trabajador(modelo):
    t_inicio = time.perf_counter()
    sys.path.insert(0, str(CAVIDADES))
    import random
    random.seed(7)
    from PyQt5.QtCore import QEventLoop, QTimer
    from PyQt5.QtWidgets import QApplication
    from visor.visor_cavidades import VisorCavidades

    cavidad = None if modelo == "general" else modelo
    app = QApplication(sys.argv[:1])
    visor = VisorCavidades(cavidad=cavidad)
    visor.resize(1400, 860)
    visor.show()
    visor.vtk_widget.Initialize()
    visor.vtk_widget.Start()
    visor.ventana.Render()
    app.processEvents()
    primer_cuadro = time.perf_counter() - t_inicio

    def esperar(ms):
        bucle = QEventLoop()
        QTimer.singleShot(ms, bucle.quit)
        bucle.exec_()

    esperar(400)
    cam = visor.renderer.GetActiveCamera()
    t0 = time.perf_counter()
    for _ in range(PASOS_GIRO):
        cam.Azimuth(360.0 / PASOS_GIRO)
        cam.OrthogonalizeViewUp()
        visor.ventana.Render()
    fps = PASOS_GIRO / (time.perf_counter() - t0)

    ancho, alto = visor.vtk_widget.width(), visor.vtk_widget.height()
    visor.vista("lateral_izquierda")
    esperar(300)
    latencias, aciertos = [], 0
    for _ in range(CLICS):
        x = int(ancho * random.uniform(0.3, 0.7))
        y = int(alto * random.uniform(0.3, 0.7))
        t0 = time.perf_counter()
        visor.clic(x, y)
        latencias.append((time.perf_counter() - t0) * 1000)
        aciertos += visor.seleccion is not None
    print("RESULTADO " + json.dumps({
        "primer_cuadro_s": round(primer_cuadro, 3), "carga_visor_s": round(visor.tiempo_carga, 3), "fps_giro_360": round(fps, 2),
        "latencia_seleccion_mediana_ms": round(statistics.median(latencias), 2),
        "latencia_seleccion_max_ms": round(max(latencias), 2), "clics_con_seleccion": aciertos,
        "estructuras": len(visor.escena.actores), "fichas_desde": visor.repositorio.origen}), flush=True)
    os._exit(0)


def conductor(args):
    filas = []
    for modelo in args.modelos:
        for rep in range(1, args.repeticiones + 1):
            t0 = time.perf_counter()
            p = subprocess.run([sys.executable, str(AQUI), "--trabajador", "--modelo", modelo],
                               capture_output=True, text=True, timeout=300, cwd=str(CAVIDADES.parent))
            total = time.perf_counter() - t0
            linea = next((l for l in p.stdout.splitlines() if l.startswith("RESULTADO ")), None)
            if p.returncode != 0 or not linea:
                filas.append({"modelo": modelo, "repeticion": rep, "error": f"codigo {p.returncode}: {(p.stderr or p.stdout)[-200:]}"})
                print(modelo, rep, "ERROR", p.returncode, flush=True)
                continue
            fila = {"modelo": modelo, "repeticion": rep, "proceso_hasta_primer_cuadro_s": round(total - 0, 2)}
            fila.update(json.loads(linea[len("RESULTADO "):]))
            filas.append(fila)
            print(modelo, rep, fila["primer_cuadro_s"], "s", fila["fps_giro_360"], "fps", fila["latencia_seleccion_mediana_ms"], "ms", flush=True)
    campos = []
    for f in filas:
        for k in f:
            if k not in campos:
                campos.append(k)
    with open(args.csv, "w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=campos)
        w.writeheader()
        w.writerows(filas)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--trabajador", action="store_true")
    ap.add_argument("--modelo", default="toracica")
    ap.add_argument("--modelos", nargs="+", default=["toracica", "abdominal", "inguinal", "general"])
    ap.add_argument("--repeticiones", type=int, default=5)
    ap.add_argument("--csv", default="rendimiento.csv")
    a = ap.parse_args()
    trabajador(a.modelo) if a.trabajador else conductor(a)
