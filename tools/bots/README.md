# Bots autónomos (20 jugadores)

Simulación de apicultores bot con tres arquetipos de decisión (estilo idle/strategy AI):

| Arquetipo | Login | Expansión | Notas |
|-----------|-------|-----------|--------|
| **casual** (poco activo) | ~38 %/día (vuelve tras 2–3 días) | máx. 6 terrenos, pocas colmenas | Solo región natal |
| **regular** (activo) | ~72 %/día | máx. 10 terrenos | Ritmo de jugador típico |
| **competitive** | ~94 %/día | máx. 30 terrenos, llena hex | Puede abrir la otra región |

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

1. Producción de miel en cada colmena (tope por alzas).
2. Cosecha → almacén por flora.
3. Venta al mercado global (umbral y fracción según persona).
4. Compra de alzas si la colmena se llena.
5. Compra de colmenas en terrenos con hueco.
6. Compra de terreno si hay saldo y cupo (precio = base 1000 + prima por flora desbloqueada).

Estado local: `tools/bots/state.json` (gitignore). Credenciales de Auth ahí.

## GitHub Actions (1 vez al día, ~gratis)

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
