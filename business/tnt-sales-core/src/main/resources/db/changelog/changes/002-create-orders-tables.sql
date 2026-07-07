-- liquibase formatted sql
-- changeset manfouo-braun:002-orders-tables
-- comment: TiiBnTick SalesOrder header and lines tables
CREATE TABLE IF NOT EXISTS sales.orders (
    id                        UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id                 UUID          NOT NULL,
    organization_id           UUID          NOT NULL,
    agency_id                 UUID          NOT NULL,
    client_third_party_id     UUID          NOT NULL,
    order_number              VARCHAR(50)   NOT NULL,
    status                    VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
    priority                  VARCHAR(10)   NOT NULL DEFAULT 'NORMAL',
    payment_status            VARCHAR(20)   NOT NULL DEFAULT 'UNPAID',
    currency                  CHAR(3)       NOT NULL DEFAULT 'XAF',
    subtotal_amount           NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_amount              NUMERIC(19,4) NOT NULL DEFAULT 0,
    mission_id                UUID,
    invoice_id                UUID,
    return_reason             VARCHAR(30),
    return_note               TEXT,
    cancel_reason             TEXT,
    delivery_street           VARCHAR(255),
    delivery_quartier         VARCHAR(100),
    delivery_city             VARCHAR(100)  NOT NULL,
    delivery_country          VARCHAR(100)  NOT NULL DEFAULT 'Cameroon',
    delivery_landmark         VARCHAR(500),
    delivery_latitude         DOUBLE PRECISION,
    delivery_longitude        DOUBLE PRECISION,
    delivery_recipient_name   VARCHAR(255),
    delivery_recipient_phone  VARCHAR(30),
    confirmed_at              TIMESTAMPTZ,
    delivered_at              TIMESTAMPTZ,
    returned_at               TIMESTAMPTZ,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sales_orders_tenant_number UNIQUE (tenant_id, order_number)
);

CREATE INDEX IF NOT EXISTS idx_orders_tenant_id           ON sales.orders (tenant_id);
CREATE INDEX IF NOT EXISTS idx_orders_tenant_status       ON sales.orders (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_tenant_client       ON sales.orders (tenant_id, client_third_party_id);
CREATE INDEX IF NOT EXISTS idx_orders_tenant_agency       ON sales.orders (tenant_id, agency_id);
CREATE INDEX IF NOT EXISTS idx_orders_mission_id          ON sales.orders (mission_id);
CREATE INDEX IF NOT EXISTS idx_orders_created_at          ON sales.orders (created_at);

CREATE TABLE IF NOT EXISTS sales.order_lines (
    id           UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    order_id     UUID          NOT NULL REFERENCES sales.orders(id) ON DELETE CASCADE,
    product_id   UUID          NOT NULL,
    product_name VARCHAR(255),
    sku          VARCHAR(100),
    quantity     NUMERIC(19,4) NOT NULL,
    unit_price   NUMERIC(19,4) NOT NULL,
    line_amount  NUMERIC(19,4) NOT NULL,
    currency     CHAR(3)       NOT NULL DEFAULT 'XAF',
    notes        TEXT
);

CREATE INDEX IF NOT EXISTS idx_order_lines_order_id ON sales.order_lines (order_id);
-- rollback DROP TABLE IF EXISTS sales.order_lines; DROP TABLE IF EXISTS sales.orders;
