# Simulador económico autónomo

`autonomous_economy_sim.js` simula jugadores que juegan de forma automática durante varios días. No se conecta a Firebase, no modifica partidas reales y no automatiza cuentas.

## Qué incluye

- producción y cosecha de colmenas;
- salud, varroa, alimentación, tratamiento y cambio de reina;
- venta al mercado regional con demanda compartida;
- comandas con precio de compra y flete;
- contratos de polinización;
- compra y ampliación de apiarios;
- colmenas, alzas, almacenes y camiones;
- cultivos anuales y árboles, incluido el coste de mantenimiento;
- trashumancia y división de colmenas;
- XP y subida de nivel, usando el mismo crecimiento `1,10` y multiplicador de coste `0,378` que la app.

**No incluye ferias ni publicidad.** No hay ninguna bonificación, evento ni compra de ese tipo en el simulador.

## Ejecución

Desde la raíz del proyecto:

```bash
node tools/simulation/autonomous_economy_sim.js --players 100 --days 365
```

Para empezar desde el estado guardado de los bots de `tools/bots/state.json`:

```bash
node tools/simulation/autonomous_economy_sim.js --players 100 --days 365 --profiles bots
```

Para una muestra pequeña:

```bash
node tools/simulation/autonomous_economy_sim.js --players 20 --days 90 --persona mixed
```

Opciones principales:

- `--players N`: número de jugadores sintéticos;
- `--days N`: días simulados;
- `--seed N`: semilla reproducible;
- `--profiles fresh|bots`: jugadores nuevos o estado de bots repetido;
- `--persona smart|steady|mixed`: política de decisión;
- `--max-parcels N` y `--max-hives N`: límites artificiales; `0` significa sin límite;
- `--hive-ramp 5@100,7@200,10@365`: cambia las compras diarias por fases;
- `--capital-multiplier N`: escala la caja inicial de jugadores nuevos; no modifica precios ni costes;
- `--hive-actions-per-day N`: permite comprar varias colmenas por día para escenarios de expansión;
- `--hive-priority`: prioriza las colmenas y abre un apiario nuevo solo cuando no queda hueco;
- `--start YYYY-MM-DD`: fecha inicial;
- `--out RUTA`: carpeta de resultados.

## Decisiones de la IA

Cada jugador reserva una caja de seguridad y, cuando la producción lo justifica:

1. instala o amplía un almacén y compra el camión necesario;
2. compra colmenas con una estimación de ingresos a 30 días;
3. añade apiarios solo si el coste y el margenRegional lo permiten;
4. vende primero al mercado y aprovecha comandas cuando superan el precio mayorista tras flete;
5. acepta polinización si el pago esperado cubre viaje y oportunidad de producción;
6. mantiene salud, varroa, reina, alzas, cultivos y trashumancia;
7. divide colmenas maduras cuando hay hueco en un terreno.

La política es deliberadamente conservadora con la caja: no maximize una estadística a costa de dejar al jugador sin liquidez.

## Resultados

La carpeta de salida contiene:

- `daily.csv`: caja, patrimonio, nivel, colmenas, producción, ventas e ingresos de cada día;
- `players.csv`: estado final de cada jugador;
- `summary.json`: métricas agregadas, niveles y diez jugadores con mayor patrimonio.

El patrimonio usa una valoración conservadora: caja + inventario a precio de mercado + 50 % del valor pagado por apiarios, almacenes, colmenas y flota. No significa que todos esos activos puedan venderse inmediatamente por ese importe.

## Qué es exacto y qué es aproximado

El script reutiliza los parámetros de `app/src/main/assets/game_balance.json` para producción, demanda, capacidad regional, XP de miel y costes logísticos. Los precios base, polinización, cosechas y decisiones de inversión usan una aproximación estratégica para que el simulador sea rápido y reproducible.

No se replica exactamente:

- el mapa hexagonal y las rutas reales;
- el clima, temperatura y floración por terreno;
- la disponibilidad finita de cada oferta NPC;
- los eventos de Firebase y el estado del mercado entre sesiones;
- todas las reglas de trivia, Ports/HQ y pantallas de interfaz.

El resultado sirve para responder preguntas de balance —¿cuánto produce un jugador, cuándo recupera la inversión, qué nivel alcanza y qué compra le resulta rentable?—, no para predecir al 100 % una partida real.
