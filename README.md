# Apiculture Simulator (Android Java)

Juego de simulacion y gestion de explotacion apicola con enfoque multijugador.

## Arquitectura

- `presentation/`: Fragments + ViewModels (MVVM).
- `domain/`: logica de negocio del juego (estaciones, balance de produccion).
- `data/local`: Room (entidades, DAO, base de datos).
- `data/repository`: integracion Room + Firebase.
- `data/remote`: modelos ligeros de Firestore.

## Modulos implementados

1. **Autenticacion**
   - Login/registro con Firebase Auth.
   - Pantalla: `LoginFragment`.
2. **Dashboard**
   - Muestra dia y estacion activa.
3. **Gestion de colmenas**
   - Crear colmena base.
   - Guardar en Room y sincronizar en Firestore.
4. **Mapa compartido**
   - Google Maps con markers de colmenas sincronizadas.
   - Permiso de ubicacion en tiempo de ejecucion.
5. **Ranking multijugador**
   - Lectura de jugadores desde Firestore.
6. **Mercado y eventos**
   - Base de UI lista para extender reglas economicas y eventos dinamicos.

## Configuracion de claves

### 1) Firebase

1. Crea un proyecto Firebase.
2. Registra la app Android con `applicationId = com.apiculture.simulator`.
3. Descarga `google-services.json` y colocalo en:
   - `app/google-services.json`
4. Habilita:
   - Authentication (Email/Password)
   - Cloud Firestore

### 2) Google Maps

1. Crea una API key en Google Cloud para **Maps SDK for Android**.
2. En `app/build.gradle`, cambia:
   - `manifestPlaceholders["MAPS_API_KEY"] = "TU_API_KEY"`

## Modelo de datos (base)

- `HiveEntity`: colmena con `lat/lng`, salud, reina, produccion.
- `PlayerEntity`: jugador y acumulados economicos.
- `LocationEntity`: ubicaciones de flora (reales o virtualizadas).
- `HoneyBatchEntity`: lotes de miel.
- `GameEventEntity`: eventos (enfermedad, clima extremo, etc.).

## Privacidad y ubicacion

- Solicita `ACCESS_FINE_LOCATION` en runtime.
- Puedes usar ubicaciones virtualizadas para ocultar coordenadas reales.
- Incluye politica de privacidad antes de publicar en Play Store.

## Proximos pasos recomendados

1. Implementar `WorkManager` para avance diario automatico del tiempo de juego.
2. Añadir detalle de colmena y acciones de apicultor (alimentar, tratar, dividir, cambiar reina, recolectar).
3. Añadir RecyclerView para listas enriquecidas (colmenas, ranking, eventos).
4. Incorporar reglas avanzadas de transhumancia y coste de transporte.
