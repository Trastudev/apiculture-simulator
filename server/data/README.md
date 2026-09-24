# Catálogo autoritativo de ofertas

Estos archivos son la copia de solo lectura de los overlays y del balance que usa
el cliente:

- `hex/iberia.json`
- `hex/za.json`
- `hex/mdg.json`
- `game_balance.json`

`src/offerCatalog.js` los carga al arrancar la API. El servidor usa este
catálogo para decidir el cupo, el clima, la flora y la ventana de floración; la
app no envía parcelas ni elige reemplazos cuando `GAME_SERVER_URL` está
configurado.

Al cambiar un overlay o `game_balance.json` hay que volver a copiar esos
archivos antes de desplegar la API y reiniciar el servicio.
