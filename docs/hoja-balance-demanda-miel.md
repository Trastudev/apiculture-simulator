# Hoja de balance — demanda de miel

## 1. Objetivo

El mercado usa un **cupo regional compartido por tipo de miel**. Las ciudades no reparten un cupo microscópico: eligen precio, volumen y competencia. El precio usa un **objetivo económico de rotación**, independiente del cupo que limita la venta.

Parámetros editables: [`app/src/main/assets/game_balance.json`](../app/src/main/assets/game_balance.json), sección `honeyMarket`.

## 2. Actividad de jugadores

Para cada jugador `i` y región `r`:

```text
nivelLimitado(i) = min(max(nivel(i), 0), límiteNivel)
multiplicadorNivel(i) = 1 + factorNivel × nivelLimitado(i)
actividadJugador(i,r) = max(colmenas(i,r), actividadMínima) × multiplicadorNivel(i)
actividadRegión(r) = Σ actividadJugador(i,r)
```

- Si el jugador tiene colmenas en una región, solo cuenta para esas regiones.
- Si no tiene ninguna, cuenta una unidad en su `mapRegion` actual.
- Solo se cuentan documentos de jugadores actualizados dentro de `díasJugadorActivo`.
- Los multiplicadores de jugadores se suman; nunca se multiplican entre sí.

Valores actuales:

```text
factorNivel       = 0,03
límiteNivel       = 100
actividadMínima  = 1
díasJugadorActivo = 30
```

Ejemplos con una sola colmena:

| Nivel | Multiplicador | Actividad |
|---:|---:|---:|
| 0 | 1,00 | 1,00 |
| 10 | 1,30 | 1,30 |
| 20 | 1,60 | 1,60 |
| 40 | 2,20 | 2,20 |
| 60 | 2,80 | 2,80 |
| 100 | 4,00 | 4,00 |

Ejemplo anual medio con una sola colmena y actividad 1:

| Tipo de miel | Cupo regional | Objetivo de rotación |
|---|---:|---:|
| Mil flores (peso 1,00) | 83,02 kg/día | 2,08 kg/día |
| Miel rara (peso 0,35) | 29,06 kg/día | 1,00 kg/día (suelo; valor bruto 0,73) |

## 3. Peso de cada miel

Para una miel `f` en la región `r`:

```text
pesoRelative(f,r) = max(sueloMiel, cuotaRegional(f,r) / maxCuotaRegional(r))
```

`maxCuotaRegional` es la mayor cuota de miel de esa región. Las mieles exóticas conservan el suelo histórico para los eventos globales, pero los mercados ordinarios solo muestran las mieles regionales.

## 4. Cupo regional compartido

Primero se calcula la producción teórica de pico por colmena:

```text
producciónPico = adultosMáximos
                 × fracciónRecolectoras
                 × kgPorRecolectora
                 × impulsoFlora
                 × impulsoTemperatura
                 × multiplicadorCielo
```

Con los valores actuales:

```text
producciónPico = 6,937812 kg/día/colmena
```

Cupo por actividad y tipo:

```text
cupoRegional(f,d) = max(1; actividadRegión(r)
                    × producciónPico
                    × fracciónProducciónTípica
                    × fracciónAbsorciónClientes
                    × díasReservaCapacidad
                    × multiplicadorExtraCapacidad
                    × pesoRelative(f,r)
                    × factorEstación(d)
                    × ruidoDiario(d))
```

El mismo `cupoRegional` se ofrece a todos los mercados de la región. Vender en una ciudad lo descuenta; las otras ciudades ven el mismo cupo restante.

## 5. Objetivo económico de rotación

El precio no usa el cupo completo como oferta/demanda. Usa una rotación normal esperada:

```text
objetivoRotación(f,d) = max(1; actividadRegión(r)
                        × producciónPico
                        × fracciónProducciónTípica
                        × fracciónAbsorciónClientes
                        × pesoRelative(f,r)
                        × factorEstación(d)
                        × ruidoDiario(d))
```

Con la configuración actual, antes de aplicar el suelo de 1 kg:

```text
cupoRegional = objetivoRotación × 40
```

Cuando el objetivo calculado baja de 1 kg se activa el suelo; en ese caso el cupo es más de 40 veces el objetivo. El objetivo rota a precio neutro; el cupo deja espacio para vender con existencias acumuladas y responder a eventos.

## 6. Precio por oferta

```text
ratioOferta = ventasAyer / objetivoRotaciónAyer
ratioLimitado = limitar(ratioOferta, 0, 2)
multiplicadorPrecio = 1 + 0,25 × (1 - ratioLimitado)
precioBaseRegional = precioBaseRegionalAnterior × multiplicadorPrecio
```

Resultado:

- 0 kg vendidos: `+25 %`.
- Se vende el objetivo: `0 %`.
- Se venden el doble: `−25 %`.

## 7. Competencia por ciudad y miel

Las ciudades no dividen el cupo. Solo alteran el precio y muestran su perfil.

### Población relativa

Se compara dentro del mismo tipo de mercado: capital con capital y local con local.

```text
puntuaciónPoblación = raíz(poblaciónMercado / poblaciónMáximaDelTipo)
```

### Popularidad de la miel

```text
popularidadMiel = cuotaRegional(f) / maxCuotaRegional
```

### Competencia

```text
competencia = (ponderaciónPoblación × puntuaciónPoblación
               + ponderaciónMiel × popularidadMiel)
              / (ponderaciónPoblación + ponderaciónMiel)
```

### Ajuste de precio

```text
recargoLocal = 0,15 a 0,20 según población local
penalizaciónCompetencia = topePenalización × competencia
ajustePrecio = recargoLocal - penalizaciónCompetencia
precioMercado = precioBaseRegional × (1 + ajustePrecio)
```

Con los valores actuales:

- Gran capital + miel popular: alta competencia y precio menor.
- Pueblo pequeño + miel rara: baja competencia y normalmente precio mayor.
- El flete decide si la prima compensa la distancia.

## 8. Parámetros editables

| Clave JSON | Actual | Efecto |
|---|---:|---|
| `typicalHivesPerPlayer` | 6 | Equivalencia usada por snapshots legacy y fallback |
| `typicalOutputFraction` | 0,40 | Parte de la producción pico considerada típica |
| `customerAbsorptionFraction` | 0,75 | Absorción normal de la producción |
| `capacityDaysBuffer` | 20 | Días de producción dentro del cupo |
| `capacityExtraMultiplier` | 2 | Multiplicador extra del cupo; con 20 y 2 produce una capacidad 40× la rotación |
| `minFloraDemandVsTop` | 0,35 | Suelo de demanda relativa por miel |
| `levelDemandFactor` | 0,03 | Incremento lineal de demanda por nivel |
| `levelDemandCap` | 100 | Nivel máximo usado por el multiplicador |
| `minActivityPerPlayerRegion` | 1 | Actividad mínima de un jugador en una región |
| `activePlayerDays` | 30 | Antigüedad máxima para contar como activo |
| `dailyNoiseMin` | 0,90 | Mínimo del ruido determinista diario |
| `dailyNoiseMax` | 1,10 | Máximo del ruido determinista diario |
| `supplyPriceSpan` | 0,25 | Respuesta máxima del precio a la oferta |
| `demandWinter` | 1,12 | Demanda estacional de invierno |
| `demandSpring` | 0,93 | Demanda estacional de primavera |
| `demandSummer` | 0,87 | Demanda estacional de verano |
| `demandAutumn` | 1,06 | Demanda estacional de otoño |
| `localMarkupMin` | 0,15 | Prima mínima de mercados locales grandes |
| `localMarkupMax` | 0,20 | Prima máxima de mercados locales pequeños |
| `competitionPopulationWeight` | 0,60 | Peso del tamaño en la competencia |
| `competitionFloraWeight` | 0,40 | Peso de la popularidad de la miel |
| `competitionPricePenaltyMax` | 0,10 | Penalización máxima por competencia |

## 9. Cómo cambiarlo con seguridad

1. Cambia primero un parámetro en `game_balance.json`.
2. Reinicia completamente la aplicación para que `GameBalanceConfig` vuelva a leer el asset.
3. Ejecuta las pruebas de mercado:
   `.\gradlew.bat :app:testDebugUnitTest --tests "com.apiculture.simulator.domain.market.*"`
4. No rebajes `capacityDaysBuffer` por debajo de `10` sin simular de nuevo: los cupos por ciudad الافتراضي pueden volver a ser demasiado pequeños para una venta útil.
5. Si cambias `typicalOutputFraction` o `kgPerForagerFullFlow`, actualiza también los ejemplos de esta hoja.
6. Mantén `capacityDaysBuffer × capacityExtraMultiplier` claramente por encima de 1; actualmente es 40.

## 10. Resultado de la simulación de control

Para un jugador con una colmena y el modelo antiguo de cuotas por ciudad, en Iberia:

- 60,3 % de las combinaciones mercado/miel quedaban por debajo de 0,2 kg.
- 96,4 % quedaban por debajo de 1 kg.

El modelo implementado elimina esa fragmentación: las 2.668 combinaciones de Iberia comparten el mismo cupo por cada uno de sus 23 tipos de miel. Los mercados se diferencian por volumen, competencia y precio.
