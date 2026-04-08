Overlay predefinido de Iberia
==============================

«overlay.json» en este directorio está generado con la misma lógica que la app
(ver IberiaOverlayJsonExportTest: quitar @Ignore y ejecutar el test con JDK 17).

Incluye «seed»: true para que no caduque el TTL de caché. Si subes versionCode
en app/build.gradle o cambias MapHexOverlayConfig, hay que regenerar el JSON.

Sin overlay válido, IberiaHexOverlayStore calcula la primera vez y guarda en
filesDir.
