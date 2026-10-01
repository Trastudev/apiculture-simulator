# Grafo ligero de carreteras (GraphHopper)

Extra opcional para pintar transhumancias por vía. No va en el APK: se genera aquí y se podrá descargar en el móvil.

## Qué incluye

Autopista, autovía, nacional, comarcal, terciaria y `unclassified`.
Fuera: residencial, aceras, bici, pistas `track`.
El grafo guarda `road_class` para pintar y cronometrar cada tipo de vía.

## Requisitos

- Docker Desktop en marcha
- Unos 8 GB de RAM para Iberia completa

## Uso

```powershell
# Prueba pequeña (Valencia)
.\build.ps1 -Region valencia

# Iberia (España + Portugal + Andorra)
.\build.ps1 -Region iberia

# Madagascar
.\build.ps1 -Region madagascar

# Sudafrica (RSA + Lesoto + Eswatini)
.\build.ps1 -Region za
```

Salida: `data/<region>-car-lite.tar.gz`. Los PBF y el grafo viven en `data/` (gitignored).

## Tamaños medidos (2026-09-18, GraphHopper 10.2, perfil coche)

| Región   | OSM crudo | Solo carreteras | Grafo en disco | Descarga gzip |
|----------|-----------|-----------------|----------------|---------------|
| Valencia | 140 MB    | 11 MB           | 22 MB          | ~15 MB        |
| Iberia   | 1,8 GB    | 158 MB          | 241 MB         | **160 MB**    |
| Madagascar | 371 MB  | 22 MB           | 52 MB          | **30 MB**     |
| Sudáfrica | 522 MB   | 46 MB           | 84 MB          | **48 MB**     |

Paquetes: `data/<region>-car-lite.tar.gz`. Releases: `routing-graph-iberia-v1`, `routing-graph-madagascar-v1`, `routing-graph-za-v1`.
