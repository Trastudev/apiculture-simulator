"use strict";

const fs = require("fs");
const { Pool } = require("pg");

const text = fs.readFileSync(__dirname + "/.env", "utf8");
for (const line of text.split(/\r?\n/)) {
  const eq = line.indexOf("=");
  if (eq > 0) process.env[line.slice(0, eq)] = line.slice(eq + 1);
}

const pool = new Pool({
  host: "127.0.0.1",
  database: process.env.PGDATABASE,
  user: process.env.PGUSER,
  password: process.env.PGPASSWORD,
});

pool
  .query(
    `SELECT table_name FROM information_schema.tables
     WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
     ORDER BY table_name`
  )
  .then(async (result) => {
    for (const row of result.rows) {
      const count = await pool.query("SELECT count(*)::int AS n FROM " + row.table_name);
      if (count.rows[0].n > 0) console.log(row.table_name + " " + count.rows[0].n);
    }
    return pool.end();
  })
  .catch((err) => {
    console.error(err.message);
    process.exit(1);
  });
