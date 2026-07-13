-- liquibase formatted sql
-- changeset manfouo-braun:005-create-leaderboard-index
-- comment: Index supporting the Link leaderboard ranking query (trust_score DESC, gamification_level DESC)
CREATE INDEX IF NOT EXISTS idx_network_nodes_ranking
    ON tnt_link.network_nodes (tenant_id, trust_score DESC, gamification_level DESC);
-- rollback DROP INDEX IF EXISTS tnt_link.idx_network_nodes_ranking;
