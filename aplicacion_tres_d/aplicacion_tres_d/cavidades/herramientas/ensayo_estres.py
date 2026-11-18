"""Ensayos automatizados de cierre del visor (C08): acercar, girar, acercar.

Cada ensayo se ejecuta en un proceso aparte (así un cierre por violación de acceso se detecta por el código de
salida y no se lleva consigo al resto de ensayos) y abre el visor real (PyQt5 + VTK) con su bucle de eventos.

Uso (conductor):
    python ensayo_estres.py --modelos toracica abdominal inguinal general --ensayos 20 --csv salida.csv
    python ensayo_estres.py ... --cavidades-dir <otra copia de cavidades/>   (p. ej. el código anterior)
    python ensayo_estres.py ... --software                                    (QT_OPENGL=software)
El trabajador (`--trabajador`) lo lanza el conductor.
"""
import argparse
import csv
import os
import subprocess
import sys
import time
from pathlib import Path

AQUI = Path(__file__).resolve()
CAVIDADES = AQUI.parents[1]
PASOS_RUEDA = int(os.environ.get("ENSAYO_PASOS", "10"))     # pasos de rueda por fase
INTERVALO_MS = int(os.environ.get("ENSAYO_INTERVALO_MS", "25"))  # separación entre pasos (una rueda real: varios por transición de 0,6 s)
CODIGOS = {3221225477: "violacion de acceso (0xC0000005)", 3221226505: "corrupcion de pila (0xC0000374)",
           3221225725: "desbordamiento de pila (0xC00000FD)"}


def trabajador(cavidades_dir, modelo, semilla):
    sys.path.insert(0, str(Path(cavidades_dir)))
    import random
    random.seed(semilla)
    from PyQt5.QtCore import QEventLoop, QTimer
    from PyQt5.QtWidgets import QApplication
    from visor.visor_cavidades import VisorCavidades

    cavidad = None if modelo == "general" else modelo
    app = QApplication(sys.argv[:1])
    visor = VisorCavidades(cavidad=cavidad)
    visor.resize(1200, 800)
    visor.show()
    visor.vtk_widget.Initialize()
    visor.vtk_widget.Start()

    def esperar(ms):
        bucle = QEventLoop()
        QTimer.singleShot(ms, bucle.quit)
        bucle.exec_()

    def rueda(sentido):
        ancho, alto = visor.vtk_widget.width(), visor.vtk_widget.height()
        x = int(ancho * random.uniform(0.35, 0.65))
        y = int(alto * random.uniform(0.35, 0.65))
        visor.rueda(x, y, sentido)
        esperar(INTERVALO_MS)

    esperar(500)
    for _ in range(PASOS_RUEDA):          # acercar
        rueda(1)
    esperar(300)
    cam = visor.renderer.GetActiveCamera()
    for _ in range(12):                   # girar
        cam.Azimuth(random.uniform(8, 20))
        cam.Elevation(random.uniform(-6, 6))
        cam.OrthogonalizeViewUp()
        if visor.explosion:
            visor._tras_mover_camara()
        visor.ventana.Render()
        esperar(INTERVALO_MS)
    for _ in range(PASOS_RUEDA):          # acercar de nuevo
        rueda(1)
    esperar(300)
    for _ in range(max(1, PASOS_RUEDA // 2)):     # y alejar, que reensambla la cavidad
        rueda(-1)
    esperar(700)
    print("ENSAYO_OK", flush=True)
    sys.stdout.flush()
    os._exit(0)


def conductor(args):
    filas = []
    entorno = dict(os.environ)
    if args.software:
        entorno["QT_OPENGL"] = "software"
    if args.agresivo:
        entorno["ENSAYO_INTERVALO_MS"], entorno["ENSAYO_PASOS"] = "5", "40"
    entorno["CAVIDADES_LOG_DIR"] = str(Path(args.registros).resolve())
    for modelo in args.modelos:
        for n in range(1, args.ensayos + 1):
            t0 = time.time()
            try:
                p = subprocess.run([sys.executable, str(AQUI), "--trabajador", "--cavidades-dir", args.cavidades_dir,
                                    "--modelo", modelo, "--semilla", str(n)],
                                   capture_output=True, text=True, timeout=args.timeout, env=entorno,
                                   cwd=str(Path(args.cavidades_dir).parent))
                codigo, salida = p.returncode, (p.stdout or "") + (p.stderr or "")
                ok = codigo == 0 and "ENSAYO_OK" in salida
            except subprocess.TimeoutExpired:
                codigo, salida, ok = -999, "tiempo agotado", False
            dur = time.time() - t0
            causa = "" if ok else CODIGOS.get(codigo & 0xFFFFFFFF, f"codigo {codigo}")
            filas.append({"modelo": modelo, "ensayo": n, "resultado": "OK" if ok else "CIERRE", "codigo_salida": codigo,
                          "causa": causa, "duracion_s": round(dur, 1),
                          "ultima_linea": (salida.strip().splitlines() or [""])[-1][:120]})
            print(f"{modelo:10s} ensayo {n:2d}: {filas[-1]['resultado']:6s} codigo={codigo} {dur:5.1f}s {causa}", flush=True)
    with open(args.csv, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(filas[0].keys()))
        w.writeheader()
        w.writerows(filas)
    resumen = {}
    for fila in filas:
        r = resumen.setdefault(fila["modelo"], [0, 0])
        r[0] += 1
        r[1] += fila["resultado"] == "OK"
    for m, (total, ok) in resumen.items():
        print(f"RESUMEN {m}: {ok}/{total} sin cierre")
    return 0 if all(o == t for t, o in resumen.values()) else 1


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--trabajador", action="store_true")
    ap.add_argument("--cavidades-dir", default=str(CAVIDADES))
    ap.add_argument("--modelo", default="toracica")
    ap.add_argument("--semilla", type=int, default=1)
    ap.add_argument("--modelos", nargs="+", default=["toracica", "abdominal", "inguinal", "general"])
    ap.add_argument("--ensayos", type=int, default=20)
    ap.add_argument("--csv", default="ensayos_estres.csv")
    ap.add_argument("--registros", default="registros_ensayo")
    ap.add_argument("--timeout", type=int, default=180)
    ap.add_argument("--software", action="store_true")
    ap.add_argument("--agresivo", action="store_true", help="rueda cada 5 ms y 40 pasos por fase")
    a = ap.parse_args()
    if a.trabajador:
        trabajador(a.cavidades_dir, a.modelo, a.semilla)
    else:
        sys.exit(conductor(a))
