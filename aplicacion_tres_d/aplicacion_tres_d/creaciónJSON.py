import json
from pathlib import Path


def generate_actor_info(num_actors):
    data = {}
    for i in range(1, num_actors + 1):
        key = f"objeto_{i}"
        data[key] = {
            "object": key,
            "nombre": f"Nombre del objeto {i}",
            "descripcion": f"Descripcion del objeto {i}",
        }
    return data


if __name__ == "__main__":
    base_dir = Path(__file__).parent
    output_file = base_dir / "sistema_organosinternos.json"

    num_actors = 665
    actor_info = generate_actor_info(num_actors)

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(actor_info, f, indent=4, ensure_ascii=False)

    print(f"Archivo generado: {output_file}")
    print(json.dumps(actor_info, indent=4, ensure_ascii=False))
