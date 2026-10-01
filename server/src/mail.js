"use strict";

const crypto = require("crypto");

async function playerById(pool, id) {
  const result = await pool.query(
    "SELECT id, player_name FROM players WHERE id = $1",
    [id]
  );
  return result.rows[0] || null;
}

async function playerByExactName(pool, name) {
  const result = await pool.query(
    "SELECT id, player_name FROM players WHERE lower(player_name) = lower($1) LIMIT 1",
    [String(name || "").trim()]
  );
  return result.rows[0] || null;
}

async function relation(pool, a, b) {
  const result = await pool.query(
    `SELECT requester_id, addressee_id, status FROM friendships
      WHERE (requester_id = $1 AND addressee_id = $2)
         OR (requester_id = $2 AND addressee_id = $1)
      LIMIT 1`,
    [a, b]
  );
  return result.rows[0] || null;
}

function areFriends(row, a, b) {
  return row && row.status === "accepted"
    && ((row.requester_id === a && row.addressee_id === b)
      || (row.requester_id === b && row.addressee_id === a));
}

async function insertMessage(pool, fromId, fromName, toId, subject, body, kind) {
  const id = crypto.randomUUID();
  await pool.query(
    `INSERT INTO mail_messages
      (id, from_id, to_id, from_name, subject, body, kind, unread)
     VALUES ($1,$2,$3,$4,$5,$6,$7,true)`,
    [id, fromId, toId, fromName || "", subject, body, kind || "note"]
  );
  return id;
}

async function handle(pool, req, res, urlPath, send, readBody) {
  const me = req.authUid;
  if (!me) {
    send(res, 401, { ok: false, error: "AUTH" });
    return true;
  }
  if (req.method === "GET" && urlPath === "/mail/unread") {
    const result = await pool.query(
      `SELECT COUNT(*)::int AS n FROM mail_messages
        WHERE to_id = $1 AND unread = true AND hidden_by_recipient = false`,
      [me]
    );
    send(res, 200, { ok: true, unread: result.rows[0].n });
    return true;
  }
  if (req.method === "GET" && urlPath === "/mail/inbox") {
    const result = await pool.query(
      `SELECT * FROM mail_messages
        WHERE to_id = $1 AND hidden_by_recipient = false
        ORDER BY created_at DESC LIMIT 80`,
      [me]
    );
    send(res, 200, { ok: true, messages: result.rows });
    return true;
  }
  if (req.method === "GET" && urlPath === "/mail/sent") {
    const result = await pool.query(
      `SELECT * FROM mail_messages
        WHERE from_id = $1 AND kind = 'note' AND hidden_by_sender = false
        ORDER BY created_at DESC LIMIT 80`,
      [me]
    );
    send(res, 200, { ok: true, messages: result.rows });
    return true;
  }
  if (req.method === "POST" && urlPath === "/mail/read") {
    const body = await readBody(req);
    await pool.query(
      "UPDATE mail_messages SET unread = false WHERE id = $1 AND to_id = $2",
      [String(body.id || ""), me]
    );
    send(res, 200, { ok: true });
    return true;
  }
  if (req.method === "POST" && urlPath === "/mail/delete") {
    const body = await readBody(req);
    const id = String(body.id || "");
    const found = await pool.query("SELECT * FROM mail_messages WHERE id = $1", [id]);
    const row = found.rows[0];
    if (!row || (row.from_id !== me && row.to_id !== me)) {
      send(res, 404, { ok: false, error: "NOT_FOUND" });
      return true;
    }
    const hideSender = row.hidden_by_sender || row.from_id === me;
    const hideRecipient = row.hidden_by_recipient || row.to_id === me;
    if (row.to_id === me && row.kind === "friend_request" && !row.reply) {
      await pool.query(
        `DELETE FROM friendships
          WHERE requester_id = $1 AND addressee_id = $2 AND status = 'pending'`,
        [row.from_id, me]
      );
    }
    if (hideSender && hideRecipient) {
      await pool.query("DELETE FROM mail_messages WHERE id = $1", [id]);
    } else {
      await pool.query(
        `UPDATE mail_messages
            SET hidden_by_sender = $2,
                hidden_by_recipient = $3,
                unread = CASE WHEN $4 THEN false ELSE unread END
          WHERE id = $1`,
        [id, hideSender, hideRecipient, row.to_id === me]
      );
    }
    send(res, 200, { ok: true });
    return true;
  }
  if (req.method === "GET" && urlPath === "/friends") {
    const result = await pool.query(
      `SELECT p.id, p.player_name
         FROM friendships f
         JOIN players p ON p.id = CASE WHEN f.requester_id = $1 THEN f.addressee_id ELSE f.requester_id END
        WHERE f.status = 'accepted' AND (f.requester_id = $1 OR f.addressee_id = $1)
        ORDER BY p.player_name`,
      [me]
    );
    send(res, 200, { ok: true, friends: result.rows });
    return true;
  }
  if (req.method === "GET" && urlPath === "/friends/status") {
    const params = new URL(req.url, "http://localhost").searchParams;
    const otherId = String(params.get("playerId") || "");
    const other = await playerById(pool, otherId);
    if (!other || other.id === me) {
      send(res, 200, { ok: true, found: false });
      return true;
    }
    const row = await relation(pool, me, other.id);
    let link = "none";
    if (areFriends(row, me, other.id)) link = "friend";
    else if (row && row.status === "pending") link = "pending";
    send(res, 200, {
      ok: true,
      found: true,
      playerId: other.id,
      playerName: other.player_name,
      link,
    });
    return true;
  }
  if (req.method === "GET" && urlPath === "/friends/lookup") {
    const params = new URL(req.url, "http://localhost").searchParams;
    const found = await playerByExactName(pool, params.get("name"));
    if (!found || found.id === me) {
      send(res, 200, { ok: true, found: false });
      return true;
    }
    const row = await relation(pool, me, found.id);
    let link = "none";
    if (areFriends(row, me, found.id)) link = "friend";
    else if (row && row.status === "pending") link = "pending";
    send(res, 200, {
      ok: true,
      found: true,
      playerId: found.id,
      playerName: found.player_name,
      link,
    });
    return true;
  }
  if (req.method === "POST" && urlPath === "/friends/request") {
    const body = await readBody(req);
    const target = body.playerId
      ? await playerById(pool, body.playerId)
      : await playerByExactName(pool, body.name);
    if (!target || target.id === me) {
      send(res, 404, { ok: false, error: "NOT_FOUND" });
      return true;
    }
    const row = await relation(pool, me, target.id);
    if (areFriends(row, me, target.id)) {
      send(res, 200, { ok: true, link: "friend" });
      return true;
    }
    if (row && row.status === "pending") {
      send(res, 200, { ok: true, link: "pending" });
      return true;
    }
    await pool.query(
      `INSERT INTO friendships (requester_id, addressee_id, status)
       VALUES ($1, $2, 'pending')`,
      [me, target.id]
    );
    const mine = await playerById(pool, me);
    const fromName = mine ? mine.player_name : "Un apicultor";
    await insertMessage(
      pool, me, fromName, target.id,
      "Solicitud de amistad",
      fromName + " quiere ser tu amigo.",
      "friend_request"
    );
    send(res, 200, { ok: true, link: "pending" });
    return true;
  }
  if (req.method === "POST" && urlPath === "/friends/remove") {
    const body = await readBody(req);
    const other = String(body.playerId || "");
    await pool.query(
      `DELETE FROM friendships
        WHERE (requester_id = $1 AND addressee_id = $2)
           OR (requester_id = $2 AND addressee_id = $1)`,
      [me, other]
    );
    send(res, 200, { ok: true, link: "none" });
    return true;
  }
  if (req.method === "POST" && urlPath === "/friends/respond") {
    const body = await readBody(req);
    const messageId = String(body.messageId || "");
    const accept = !!body.accept;
    const msg = await pool.query(
      "SELECT * FROM mail_messages WHERE id = $1 AND to_id = $2 AND kind = 'friend_request'",
      [messageId, me]
    );
    const row = msg.rows[0];
    if (!row) {
      send(res, 404, { ok: false, error: "NOT_FOUND" });
      return true;
    }
    if (row.reply === "accepted" || row.reply === "rejected") {
      send(res, 200, { ok: true, reply: row.reply });
      return true;
    }
    const reply = accept ? "accepted" : "rejected";
    await pool.query(
      "UPDATE mail_messages SET unread = false, reply = $2 WHERE id = $1",
      [messageId, reply]
    );
    if (accept) {
      await pool.query(
        `UPDATE friendships SET status = 'accepted'
          WHERE requester_id = $1 AND addressee_id = $2`,
        [row.from_id, me]
      );
      const mine = await playerById(pool, me);
      await insertMessage(
        pool, me, mine ? mine.player_name : "", row.from_id,
        "Amistad aceptada",
        (mine ? mine.player_name : "Un apicultor") + " ha aceptado tu amistad.",
        "note"
      );
    } else {
      await pool.query(
        `DELETE FROM friendships WHERE requester_id = $1 AND addressee_id = $2 AND status = 'pending'`,
        [row.from_id, me]
      );
    }
    send(res, 200, { ok: true, reply });
    return true;
  }
  if (req.method === "POST" && urlPath === "/mail/send") {
    const body = await readBody(req);
    const toId = String(body.toId || "");
    const subject = String(body.subject || "").trim().slice(0, 80);
    const text = String(body.body || "").trim().slice(0, 2000);
    if (!toId || !subject || !text) {
      send(res, 400, { ok: false, error: "EMPTY" });
      return true;
    }
    const row = await relation(pool, me, toId);
    if (!areFriends(row, me, toId)) {
      send(res, 403, { ok: false, error: "NOT_FRIEND" });
      return true;
    }
    const mine = await playerById(pool, me);
    await insertMessage(pool, me, mine ? mine.player_name : "", toId, subject, text, "note");
    send(res, 200, { ok: true });
    return true;
  }
  return false;
}

module.exports = { handle };
