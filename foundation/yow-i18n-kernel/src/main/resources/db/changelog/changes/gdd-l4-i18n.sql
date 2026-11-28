--liquibase formatted sql
--changeset gdd-l4:gdd-l4-419-i18n-relay-revoked-reason labels:gdd-l4
--comment: Relay point revocation with the administrator's reason (decision D-129), fr and en. Same texts as the JSON packs; ON CONFLICT DO NOTHING keeps any admin edit.
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay_status.revoked_reason', 'fr', 'DEFAULT', $i18n$Point Relais révoqué

Votre point relais a été révoqué définitivement : {{reason}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay_status.revoked_reason', 'en', 'DEFAULT', $i18n$Relay point revoked

Your relay point has been permanently revoked: {{reason}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
