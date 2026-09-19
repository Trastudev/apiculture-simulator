# Bots autónomos (20 jugadores)

Simulación de apicultores bot con tres arquetipos. Juegan como un jugador bueno: producen, cosechan, venden al mayor, instalan apiarios, compran colmenas con alzas y mueven a hexes más productivos.

**No compran comandas ni aceptan contratos de polinización.**

| Arquetipo | Login | Expansión | Notas |
|-----------|-------|-----------|--------|
| **casual** (poco activo) | ~38 %/día (vuelve tras 2–3 días) | máx. 6 apiarios | Solo región natal |
| **regular** (activo) | ~72 %/día | máx. 12 apiarios | Ritmo de jugador típico |
| **competitive** | ~94 %/día | máx. 28 apiarios | Puede abrir ZA al nivel 25 |

## Comandos

```bash
# Crear / sincronizar los 20 bots en Auth + Firestore
node tools/bots/bot_farm.js ensure

# Un día de decisiones (local; añade --live para escribir Firestore + mercado)
node tools/bots/bot_farm.js tick
node tools/bots/bot_farm.js tick --live

# Simulación multi-día (recomendado para comprobar el cerebro)
node tools/bots/bot_farm.js simulate --days 30
node tools/bots/bot_farm.js simulate --days 7 --live

# Bucle periódico en producción ligera
node tools/bots/bot_farm.js loop --hours 4
```

## Flujo diario de cada bot (si “se conecta”)

1. Producción de miel (tope por alzas, bonus por flora cara).
2. Cosecha antes de llenar del todo → almacén por flora.
3. Venta al mayor (nunca comandas).
4. Compra de alzas en las colmenas más llenas.
5. Mover colmenas al apiario propio con mejor flora / menos saturación.
6. Comprar colmenas (con 0–2 alzas si el saldo da) en el mejor apiario con hueco.
7. Instalar un apiario nuevo (hex compartible; doc `hexId::uid` + punto aleatorio).
8. Opcional: almacén (300 B) en el primer apiario.

Arranque / reset: 10 000 B, un apiario y una colmena pagada. Sin almacén automático.

Estado local: `tools/bots/state.json` (gitignore). Credenciales de Auth ahí.

## Reset global (admin en la app)

Si Aleix lanza **Reiniciar TODOS los jugadores**, Firestore publica `globalGameEvents/game_reset`.
En el próximo `tick --live` (o Action diaria) los bots detectan la generación y vuelven al starter.

**No hace falta que yo inicie sesión en tu cuenta.** Tú subes el workflow y creas 2 secrets.

1. Asegura bots locales: `node tools/bots/bot_farm.js ensure`
2. Genera secrets: `.\tools\bots\prepare_github_secrets.ps1`
3. En GitHub → **Settings → Secrets and variables → Actions**:
   - `GOOGLE_SERVICES_JSON_B64`
   - `BOTS_STATE_B64`
4. Sube el repo (incluye `.github/workflows/bots-daily.yml`)
5. **Actions → Bots daily tick → Run workflow** (prueba)
6. Cron automático: cada día ~07:00 UTC

Tras cada run hay un artifact `bots-state-*`. De vez en cuando descarga `state.json`, vuelve a generar `BOTS_STATE_B64` y actualiza el secret (para no perder contraseñas/hex nuevos).
