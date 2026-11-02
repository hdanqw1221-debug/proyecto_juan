"""Tubos digestivos relajados como campos implícitos amoldados a sus vecinos.

Antes cada tubo se mallaba sobre su línea media relajada y después se apartaban a la fuerza los
vértices que quedaban dentro de otro órgano, lo que dejaba parches rasgados. Aquí cada tubo es un
campo (cadena de cápsulas de radio variable con haustras y tenias) que, antes de mallarse, talla con
suavidad a los vecinos ya construidos: las caras de contacto quedan aplanadas, como en el animal,
donde las vísceras se amoldan unas a otras.

Orden de construcción: de mayor a menor prioridad (catalogo.PRIORIDAD); con igual prioridad se
deforma el primero por orden alfabético, como en la revisión de colisiones del generador. Cada
tubo talla a los que se construyeron antes y a los órganos macizos, salvo a los vecinos con los que
se continúa y a los órganos que tallan ellos mismos la impresión de ese tubo (los que lo declaran en
su conjunto IMPRESIONES, p. ej. el hígado con sus impresiones gástrica y cólica).
"""
import numpy as np

from anatomia import catalogo as cat
from anatomia import especificacion as E
from anatomia.organos import contexto as C
from blender import primitivas as P

PARAMETROS = {
    "holgura": 0.003,           # separación que se deja con cada vecino tallado (m)
    "aplastamiento_max": 0.6,   # fracción del radio local que puede hundirse una cara de contacto
    "suavidad": 0.010,          # radio del redondeo entre la pared del tubo y la impronta (m)
    "profundidad_tenia": 0.07,  # como en primitivas.modulacion_haustras
    "ancho_tenia": 0.16,
    # Separación entre asas del mismo tubo (yeyuno, colon descendente): las asas alejadas a lo largo
    # del arco no se fusionan aunque sus líneas medias queden cerca.
    "separacion_asas": 0.004,
}


def impresiones_de(objeto_id):
    """Estructuras cuya impresión talla `objeto_id` en sí mismo (conjunto IMPRESIONES de su módulo)."""
    mod = C.modulo(objeto_id)
    return set(getattr(mod, "IMPRESIONES", ())) if mod is not None else set()


def ventana_de_arco(ri, rj):
    """Distancia a lo largo del arco por debajo de la cual dos puntos son del mismo tramo del tubo.

    Una curva suave de radio >= r da media vuelta en ~pi r: puntos más alejados en el arco que eso y
    cercanos en el espacio son asas distintas (o una horquilla demasiado cerrada) y deben separarse.
    """
    return 1.6 * (ri + rj) + 0.01


def segmentos():
    """{objeto_id: especificación del segmento} de las cadenas digestivas relajadas."""
    return {seg["objeto_id"]: seg for cadena in E.CADENAS for seg in cadena["segmentos"]}


def en_meandro(objeto_id):
    """Tramos generados en meandro (con asas): yeyuno, íleon y colon descendente.

    Solo en ellos se separan las asas; en el resto (estómago en J, duodeno, colon mayor) la cercanía
    de sus partes es la forma buscada.
    """
    return "generador" in segmentos()[objeto_id]


def orden_de_construccion(ids):
    """Tubos de mayor a menor prioridad; con igual prioridad, el alfabéticamente primero al final."""
    return sorted(sorted(ids, reverse=True), key=lambda oid: -cat.POR_ID[oid]["prioridad"])


def _factor(s, th, haustras):
    """Factor del radio por haustras (senoide a lo largo del arco) y tenias (surcos longitudinales)."""
    f = np.ones_like(s)
    if not haustras:
        return f
    amp, per, n = haustras.get("amplitud", 0.0), haustras.get("periodo", 0.08), haustras.get("n_tenias", 0)
    if amp:
        f = f * (1.0 + amp * np.sin(2 * np.pi * s / per))
    if n:
        surco = np.zeros_like(s)
        for k in range(n):
            d = np.angle(np.exp(1j * (th - 2 * np.pi * k / n)))
            surco = surco + np.exp(-(d / PARAMETROS["ancho_tenia"]) ** 2)
        f = 1.0 + (f - 1.0) * (1.0 - surco) - PARAMETROS["profundidad_tenia"] * surco
    return f


def sdf_tubo(p, curva, radios, haustras=None, bloque=6000, separar_asas=False):
    """Distancia aproximada a un tubo de radio variable con haustras; devuelve (d, radio_local).

    Con separar_asas, cada punto se compara también con el tramo más cercano del mismo tubo que esté
    lejos a lo largo del arco (otra asa) y se talla una separación, de modo que las asas próximas no
    se fusionan (sin túneles ni puentes en la malla).
    """
    p = np.asarray(p, dtype=float)
    c = np.asarray(curva, dtype=float)
    r = np.broadcast_to(np.asarray(radios, dtype=float), (len(c),))
    _, n, b = P.marcos_transporte(c)
    a_, ab = c[:-1], c[1:] - c[:-1]
    largo = np.linalg.norm(ab, axis=1)
    s0 = np.concatenate([[0.0], np.cumsum(largo)])[:-1]
    l2 = np.maximum(largo ** 2, 1e-12)
    d_sal, r_sal = np.empty(len(p)), np.empty(len(p))
    s_medio = s0 + 0.5 * largo
    ventana = ventana_de_arco(r[:-1], r[:-1])  # tramos del mismo trozo de tubo
    for i in range(0, len(p), bloque):
        q = p[i:i + bloque, None, :]
        t = np.clip(np.sum((q - a_) * ab, axis=2) / l2, 0.0, 1.0)
        rr = r[:-1] + t * (r[1:] - r[:-1])
        dist = np.linalg.norm(q - (a_ + t[..., None] * ab), axis=2) - rr
        k = np.argmin(dist, axis=1)
        filas = np.arange(len(k))
        tk = t[filas, k]
        v = p[i:i + bloque] - (a_[k] + tk[:, None] * ab[k])
        th = np.arctan2(np.sum(v * b[k], axis=1), np.sum(v * n[k], axis=1))
        rk = rr[filas, k]
        d = np.linalg.norm(v, axis=1) - rk * _factor(s0[k] + tk * largo[k], th, haustras)
        if separar_asas and len(largo) > 2:
            otra_asa = np.abs(s_medio[None, :] - s_medio[k][:, None]) > ventana[k][:, None]
            d2 = np.where(otra_asa, dist, np.inf).min(axis=1)
            d = np.maximum(d, PARAMETROS["separacion_asas"] - d2)
        d_sal[i:i + bloque] = d
        r_sal[i:i + bloque] = rk
    return d_sal, r_sal


def restriccion_relajacion(Pp, R):
    """Mantiene los puntos de las líneas medias dentro de la cavidad abdominal (como en la relajación)."""
    from anatomia import referencia as ref

    Q = Pp.copy()
    for _ in range(2):
        x = np.clip(Q[:, 0], ref.X_MIN_ENVOLVENTE, ref.X_MAX_ENVOLVENTE)
        Q[:, 1], Q[:, 2] = ref.proyectar_dentro_envolvente(x, Q[:, 1], Q[:, 2], R + 0.01)
        n = ref.normal_diafragma(Q[:, 1], Q[:, 2])
        d = (ref.x_diafragma(Q[:, 1], Q[:, 2]) - Q[:, 0]) * n[:, 0]
        falta = np.maximum(R + 0.014 - d, 0.0)
        Q -= falta[:, None] * n
    return Q


def separar_asas(curva, radios, holgura=0.016, iteraciones=150, paso_max=0.004, restriccion=None):
    """Aparta entre sí las asas de un mismo tubo cuyas líneas medias quedaron demasiado cerca.

    La relajación conjunta de las cadenas no siempre consigue separar las asas del yeyuno o del colon
    descendente (se cruzan hasta 1 cm). Este paso local empuja los puntos alejados a lo largo del arco
    hasta dejar `holgura` entre las paredes, con los extremos fijos (uniones con los segmentos vecinos)
    y suavizando la curva. `restriccion(P, R) -> P` mantiene los puntos en la cavidad. Los solapes con
    otros tubos los resuelve después el campo de cada tubo (improntas).
    """
    P_ = np.array(curva, dtype=float, copy=True)
    R = np.asarray(radios, dtype=float)
    s = np.concatenate([[0.0], np.cumsum(np.linalg.norm(np.diff(P_, axis=0), axis=1))])
    lejos = np.abs(s[:, None] - s[None, :]) > ventana_de_arco(R[:, None], R[None, :])
    necesario = R[:, None] + R[None, :] + holgura
    movil = np.ones(len(P_), dtype=bool)
    movil[[0, -1]] = False
    for _ in range(iteraciones):
        d = P_[:, None, :] - P_[None, :, :]
        dist = np.linalg.norm(d, axis=2) + 1e-9
        solape = np.where(lejos, np.maximum(necesario - dist, 0.0), 0.0)
        desp = np.einsum("ij,ijk->ik", solape / dist, d) * 0.5
        if not desp.any():
            break
        norma = np.linalg.norm(desp, axis=1, keepdims=True)
        desp *= np.minimum(1.0, paso_max / np.maximum(norma, 1e-12))
        P_[movil] += desp[movil]
        P_[1:-1] += 0.15 * (0.5 * (P_[:-2] + P_[2:]) - P_[1:-1])
        if restriccion is not None:
            P_[movil] = restriccion(P_, R)[movil]
    return P_


def holgura_entre_asas(curva, radios):
    """Menor separación (m) entre paredes de asas del mismo tubo alejadas a lo largo del arco."""
    c, R = np.asarray(curva), np.asarray(radios)
    s = np.concatenate([[0.0], np.cumsum(np.linalg.norm(np.diff(c, axis=0), axis=1))])
    lejos = np.abs(s[:, None] - s[None, :]) > ventana_de_arco(R[:, None], R[None, :])
    D = np.linalg.norm(c[:, None] - c[None], axis=2) - R[:, None] - R[None, :]
    return float(np.where(lejos, D, np.inf).min())


def forma(objeto_id, p, ctx):
    """Tubo sin tallar sobre su línea media relajada (la usan los vecinos para sus impresiones)."""
    curva, radios = ctx.lineas[objeto_id]
    return sdf_tubo(p, curva, radios, segmentos()[objeto_id].get("haustras"), separar_asas=en_meandro(objeto_id))[0]


def campo(objeto_id, p, ctx, vecinos=()):
    """Tubo amoldado a `vecinos` (ids ya construidos) y limitado a la cavidad abdominal."""
    cfg = PARAMETROS
    curva, radios = ctx.lineas[objeto_id]
    F, r = sdf_tubo(p, curva, radios, segmentos()[objeto_id].get("haustras"), separar_asas=en_meandro(objeto_id))
    # Las improntas solo pueden subir el campo: lejos del tubo (F > 2 cm) no cambian la superficie,
    # así que los vecinos se evalúan solo cerca de él.
    cerca = F < 0.02
    q, Fc = p[cerca], F[cerca]
    tope = Fc + cfg["aplastamiento_max"] * r[cerca]  # la impronta no hunde la pared más que esa fracción del radio
    for vecino in vecinos:
        if frozenset((objeto_id, vecino)) in cat.UNIONES or objeto_id in impresiones_de(vecino):
            continue
        impronta = np.minimum(cfg["holgura"] - ctx.sdf(vecino)(q), tope)
        Fc = -P.smin(-Fc, -impronta, cfg["suavidad"])
    F = F.copy()
    F[cerca] = -P.smin(-Fc, -C.restriccion_cavidad(q, "abdominal", 0.008), cfg["suavidad"])
    return F


def pieza(objeto_id, ctx):
    """Caja y paso de rejilla de un tubo según su línea media y sus radios."""
    curva, radios = ctx.lineas[objeto_id]
    hau = segmentos()[objeto_id].get("haustras") or {}
    margen = float(np.max(radios)) * (1.0 + hau.get("amplitud", 0.0)) + 0.02
    rmin = float(np.min(radios))
    # Unas 6 celdas por radio: conserva las haustras, las improntas y la separación entre asas (el remallado
    # por vóxeles de Blender cierra los huecos de menos de ~2 vóxeles).
    h = float(np.clip(rmin / 6.0, 0.004, 0.008))
    if hau.get("amplitud"):
        h = min(h, hau.get("periodo", 0.08) / 9.0)
    return {"caja": (curva.min(0) - margen, curva.max(0) + margen), "h": h, "sigma": 0.6, "recorte": "abdominal",
            "radio_min": rmin, "voxel": h}
