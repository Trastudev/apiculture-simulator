Semillas del overlay de hexágonos (opcional)
===========================================

Copia aquí los .json generados por la app (ruta interna: files/hex_overlay/).
El nombre del fichero debe ser exactamente el mismo (hash .json).

Tras copiar desde el dispositivo, abre el JSON y añade en la raíz:
  "seed": true
Así no caduca el TTL de 7 días.

Al subir versionCode de la app, la clave interna cambia: hay que volver a generar
y empaquetar semillas para esa versión, o dejar la carpeta vacía (solo caché en runtime).
