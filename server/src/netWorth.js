"use strict";

function hivePrice() {
  return 200;
}

function warehouseInvested(level) {
  const lvl = Math.max(0, Number(level) || 0);
  if (lvl <= 0) return 0;
  let sum = 1500;
  for (let from = 1; from < lvl; from++) sum += 250 * Math.max(1, from);
  return sum;
}

/**
 * Patrimonio calculado en el servidor: terreno, almacén y colmenas de Postgres.
 * Nunca baja lo que el teléfono ya publicó: este cálculo no incluye flota ni el
 * precio real del terreno, y un 0 por parcelas aún no subidas no debe borrar el ranking.
 */
async function saveDaily(pool, ownerId, dayKey) {
  const parcels = await pool.query(
    `SELECT hex_id,
            bool_or(has_warehouse) AS has_warehouse,
            max(warehouse_level) AS warehouse_level
       FROM hex_parcels
      WHERE owner_id = $1
      GROUP BY hex_id`,
    [ownerId]
  );
  const hives = await pool.query(
    "SELECT super_count FROM hives WHERE owner_id = $1",
    [ownerId]
  );
  let worth = 0;
  for (const row of parcels.rows) {
    if (!row.hex_id || String(row.hex_id).includes("_za_")) continue;
    worth += 1000;
    if (row.has_warehouse) worth += warehouseInvested(row.warehouse_level || 1);
  }
  for (const hive of hives.rows) worth += hivePrice(hive.super_count);
  const saved = await pool.query(
    `UPDATE players
        SET net_worth_b = GREATEST(net_worth_b, $2),
            net_worth_day_key = $3,
            updated_at = now()
      WHERE id = $1
      RETURNING net_worth_b`,
    [ownerId, worth, dayKey]
  );
  return saved.rowCount > 0 ? Number(saved.rows[0].net_worth_b) : worth;
}

module.exports = { saveDaily };
