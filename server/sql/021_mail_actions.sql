ALTER TABLE mail_messages ADD COLUMN IF NOT EXISTS reply text;

ALTER TABLE mail_messages ADD COLUMN IF NOT EXISTS hidden_by_sender boolean NOT NULL DEFAULT false;

ALTER TABLE mail_messages ADD COLUMN IF NOT EXISTS hidden_by_recipient boolean NOT NULL DEFAULT false;
