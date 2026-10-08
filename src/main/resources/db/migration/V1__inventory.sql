CREATE TABLE inventory_item (
    id UUID PRIMARY KEY,
    sku VARCHAR(40) NOT NULL CHECK (sku ~ '^[A-Z0-9][A-Z0-9_-]{0,39}$'),
    name VARCHAR(100) NOT NULL CHECK (length(trim(name)) > 0),
    detail VARCHAR(180) NOT NULL DEFAULT '',
    category VARCHAR(20) NOT NULL CHECK (category IN ('アルバム','フォトカード','ライブグッズ','アパレル','その他')),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 0 AND 99999999),
    minimum INTEGER NOT NULL CHECK (minimum BETWEEN 0 AND 99999999),
    price INTEGER NOT NULL CHECK (price BETWEEN 0 AND 99999999),
    location VARCHAR(40) NOT NULL DEFAULT '',
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(64)
);
CREATE UNIQUE INDEX inventory_active_sku ON inventory_item (sku) WHERE deleted_at IS NULL;
CREATE INDEX inventory_active_updated ON inventory_item (updated_at DESC, id) WHERE deleted_at IS NULL;
CREATE INDEX inventory_active_category ON inventory_item (category) WHERE deleted_at IS NULL;
CREATE TABLE inventory_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES inventory_item(id),
    actor VARCHAR(64) NOT NULL,
    action VARCHAR(16) NOT NULL CHECK (action IN ('CREATE','UPDATE','DELETE','RESTORE')),
    version BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX inventory_audit_item ON inventory_audit(item_id, occurred_at);

DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'zaikou_app') THEN
        CREATE ROLE zaikou_app NOLOGIN;
    END IF;
END $$;
GRANT USAGE ON SCHEMA public TO zaikou_app;
GRANT SELECT, INSERT, UPDATE ON inventory_item TO zaikou_app;
GRANT INSERT ON inventory_audit TO zaikou_app;
GRANT USAGE ON SEQUENCE inventory_audit_id_seq TO zaikou_app;
