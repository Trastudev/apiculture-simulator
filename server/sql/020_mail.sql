CREATE TABLE IF NOT EXISTS friendships (
  requester_id text NOT NULL,
  addressee_id text NOT NULL,
  status text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (requester_id, addressee_id)
);

CREATE TABLE IF NOT EXISTS mail_messages (
  id text PRIMARY KEY,
  from_id text NOT NULL,
  to_id text NOT NULL,
  from_name text NOT NULL DEFAULT '',
  subject text NOT NULL,
  body text NOT NULL,
  kind text NOT NULL DEFAULT 'note',
  unread boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);
