"""Estómago del caballo: saco en J, aplanado craneocaudalmente, alrededor de su línea media relajada.

Especificación de la forma (Bradley 1922, fig. 52 p113; Sisson y Grossman; Dyce–Sack–Wensing;
docs/investigacion_anatomica_cavidades_equinas.md §3.2):
- Saco fuertemente curvado sobre sí mismo (entrada y salida muy próximas): curvatura mayor larga y
  convexa, curvatura menor muy corta; casi todo a la izquierda del plano medio, en el abdomen
  intratorácico, caudal al diafragma y al hígado.
- Saco ciego (saccus caecus): cúpula dorsal izquierda que se eleva por encima del cardias, contra la
  parte dorsal izquierda del diafragma, en continuidad con el fundus (sin cuello).
- Cardias en la curvatura menor (allí termina el esófago prolongado: unión anatómica).
- Porción pilórica que se estrecha hacia la derecha y anillo del píloro antes del duodeno.
- Caras parietal (craneal, contra hígado y diafragma) y visceral (caudal, contra el intestino):
  sección elíptica aplanada en sentido craneocaudal.
Su línea media se relaja con las demás cadenas digestivas (especificacion.CADENAS); el campo se
construye alrededor de ctx.lineas["ABD_ESTOMAGO"] = (curva, radios) y talla, como los tubos, a los
órganos macizos vecinos que no declaren tallarlo ellos mismos (IMPRESIONES).
"""
import numpy as np

from anatomia import catalogo as cat
from anatomia.organos import contexto as C
from blender import primitivas as P

USAR_CAMPO = True
FORMA_CON_CONTEXTO = True  # la forma depende de la línea media relajada

PARAMETROS = {
    "aplanamiento": 0.80,      # semieje craneocaudal / semieje transversal de la sección
    # Saco ciego: aumento del radio en el primer tramo (m) y longitud sobre la que se reparte.
    "saco_ciego": {"aumento": 0.024, "longitud": 0.16},
    # Píloro: anillo de constricción a `distancia` del final (m), semiancho y fracción del radio.
    "piloro": {"distancia": 0.045, "ancho": 0.012, "constriccion": 0.28},
    "holgura": 0.003,          # separación con cada vecino tallado (m)
    "aplastamiento_max": 0.45,  # fracción del radio que puede hundirse una cara de contacto
    "suavidad": 0.012,
}

# Órganos macizos que el estómago talla si no lo tallan ellos (IMPRESIONES de su módulo).
VECINOS = ("ABD_BAZO", "ABD_PANCREAS", "ABD_RINON_DER", "ABD_RINON_IZQ", "ABD_ADRENAL_DER", "ABD_ADRENAL_IZQ",
           "ABD_HIGADO")


def _perfil(curva, radios):
    """Arco de cada punto y radio modificado: saco ciego y píloro."""
    cfg = PARAMETROS
    c = np.asarray(curva, dtype=float)
    s = np.concatenate([[0.0], np.cumsum(np.linalg.norm(np.diff(c, axis=0), axis=1))])
    L = s[-1]
    r = np.asarray(radios, dtype=float).copy()
    sc = cfg["saco_ciego"]
    u = np.clip(1.0 - s / sc["longitud"], 0.0, 1.0)
    r += sc["aumento"] * u * u * (3 - 2 * u)
    pi = cfg["piloro"]
    r *= 1.0 - pi["constriccion"] * np.exp(-((s - (L - pi["distancia"])) / pi["ancho"]) ** 2)
    return s, r


def _sdf_saco(p, curva, radios, bloque=6000):
    """Distancia aproximada al saco: cadena de cápsulas de sección elíptica aplanada en x.

    Devuelve (d, radio_local)."""
    k = PARAMETROS["aplanamiento"]
    c = np.asarray(curva, dtype=float)
    _, R = _perfil(c, radios)
    a_, ab = c[:-1], c[1:] - c[:-1]
    l2 = np.maximum(np.sum(ab * ab, axis=1), 1e-12)
    t = ab / np.sqrt(l2)[:, None]
    # Eje del aplanamiento: el craneocaudal (x) perpendicular a la línea media.
    e = np.array([1.0, 0.0, 0.0])[None, :] - t[:, :1] * t
    e /= np.maximum(np.linalg.norm(e, axis=1, keepdims=True), 1e-9)
    p = np.asarray(p, dtype=float)
    d_sal, r_sal = np.empty(len(p)), np.empty(len(p))
    for i in range(0, len(p), bloque):
        q = p[i:i + bloque, None, :]
        tt = np.clip(np.sum((q - a_) * ab, axis=2) / l2, 0.0, 1.0)
        v = q - (a_ + tt[..., None] * ab)
        ve = np.sum(v * e, axis=2)
        resto2 = np.maximum(np.sum(v * v, axis=2) - ve * ve, 0.0)
        rr = R[:-1] + tt * (R[1:] - R[:-1])
        d = np.sqrt((ve / k) ** 2 + resto2) - rr
        j = np.argmin(d, axis=1)
        filas = np.arange(len(j))
        d_sal[i:i + bloque] = d[filas, j]
        r_sal[i:i + bloque] = rr[filas, j]
    return d_sal, r_sal


def piezas(ctx):
    curva, radios = ctx.lineas["ABD_ESTOMAGO"]
    margen = float(np.max(radios)) + PARAMETROS["saco_ciego"]["aumento"] + 0.03
    return {"ABD_ESTOMAGO": {"caja": (curva.min(0) - margen, curva.max(0) + margen), "h": 0.006, "sigma": 1.0,
                             "recorte": "abdominal", "voxel": 0.006}}


def forma(objeto_id, p, ctx):
    curva, radios = ctx.lineas["ABD_ESTOMAGO"]
    return _sdf_saco(p, curva, radios)[0]


def campo(objeto_id, p, ctx):
    from anatomia.organos import tubos  # conjunto IMPRESIONES de los vecinos

    cfg = PARAMETROS
    curva, radios = ctx.lineas[objeto_id]
    F, r = _sdf_saco(p, curva, radios)
    cerca = F < 0.02  # las improntas solo suben el campo: lejos del saco no cambian la superficie
    q, Fc = p[cerca], F[cerca]
    tope = Fc + cfg["aplastamiento_max"] * r[cerca]
    for vecino in VECINOS:
        if frozenset((objeto_id, vecino)) in cat.UNIONES or objeto_id in tubos.impresiones_de(vecino):
            continue
        impronta = np.minimum(cfg["holgura"] - ctx.sdf(vecino)(q), tope)
        Fc = -P.smin(-Fc, -impronta, cfg["suavidad"])
    F = F.copy()
    F[cerca] = -P.smin(-Fc, -C.restriccion_cavidad(q, "abdominal", 0.010), cfg["suavidad"])
    return F
