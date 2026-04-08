Parcelas hexagonales — máscara de tierra
=====================================

Por defecto el mapa usa «ne_10m_land.geojson» (Natural Earth 10m): costa mucho más
fiel que 110m; el APK gana ~10 MB. Alternativa ligera: «ne_110m_land.geojson»
(cambia LandMaskAssets.DEFAULT_LAND_GEOJSON_ASSET a LAND_GEOJSON_110M_ASSET en código).

Fuentes:
  10m — https://www.naturalearthdata.com/downloads/10m-physical-vectors/
  110m — https://www.naturalearthdata.com/downloads/110m-physical-vectors/

Mirror GeoJSON (misma licencia): repositorio nvkelso/natural-earth-vector (rama v5.1.2).

En código:
  LandMaskAssets.loadGeoJson(context, LandMaskAssets.DEFAULT_LAND_GEOJSON_ASSET);

Si falta el archivo, el mapa usa un rectángulo ibérico aproximado (menos fiable).
