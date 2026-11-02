"""Formas de los órganos como campos implícitos (solo numpy; los usan Blender y el prototipador).

Cada módulo de órgano expone la misma interfaz:

    PIEZAS = {objeto_id: {"caja": (mín, máx), "h": paso de rejilla, "sigma": desenfoque en celdas,
                          "recorte": "toracica" | "abdominal" | None, ...opcionales}}
        Opcionales: "taubin" (iteraciones, 4 por defecto), "voxel" (remallado), "tris_max", "radio_min".
    forma(objeto_id, p) -> (N,)
        Forma base sin contexto (< 0 dentro, en metros aproximados). La usan los vecinos para tallar
        sus impresiones y la relajación de los tubos digestivos como obstáculo, así que debe ser
        barata y no depender de otros órganos.
    campo(objeto_id, p, ctx) -> (N,)
        Forma final que se malla: la base con las impresiones de los vecinos (ctx: contexto.Contexto)
        y limitada a su cavidad.

`contexto.Contexto` da acceso a las formas de cualquier estructura (órganos y tubos) por su objeto_id.
"""
