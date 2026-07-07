--liquibase formatted sql
--changeset MANFOUO_Braun:004_create_dispute_comments_table
--comment: Create tnt_dispute_comments table — discussion thread between parties and mediator

CREATE TABLE IF NOT EXISTS tnt_dispute_comments (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    dispute_id      VARCHAR(36)     NOT NULL,
    tenant_id       VARCHAR(100)    NOT NULL,
    author_id       VARCHAR(100)    NOT NULL,
    author_type     VARCHAR(30)     NOT NULL,
    content         TEXT            NOT NULL,
    is_internal     BOOLEAN         NOT NULL DEFAULT FALSE,
    posted_at       TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_comment_dispute
        FOREIGN KEY (dispute_id) REFERENCES tnt_disputes(id) ON DELETE CASCADE
);

--rollback DROP TABLE IF EXISTS tnt_dispute_comments;
