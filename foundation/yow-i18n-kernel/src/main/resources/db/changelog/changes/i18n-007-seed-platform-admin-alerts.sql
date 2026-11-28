--liquibase formatted sql
--changeset MANFOUO_Braun:i18n-007-seed-platform-admin-alerts
--comment: Decision D-172 and the maintainer directive of 2026-11-27: TNT_ADMIN grant/revocation alerts to the beneficiary and the system-tenant owner, as an e-mail (subject + body) and an in-app message, in each recipient's own language. Re-runnable: only an untouched seed row is refreshed.
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.roles.platform_admin.granted', 'fr', 'EMAIL_SUBJECT', $i18n$TiiBnTick — rôle TNT_ADMIN attribué au compte {{userId}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.roles.platform_admin.granted', 'en', 'EMAIL_SUBJECT', $i18n$TiiBnTick — TNT_ADMIN role granted to account {{userId}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.roles.platform_admin.revoked', 'fr', 'EMAIL_SUBJECT', $i18n$TiiBnTick — rôle TNT_ADMIN révoqué pour le compte {{userId}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.roles.platform_admin.revoked', 'en', 'EMAIL_SUBJECT', $i18n$TiiBnTick — TNT_ADMIN role revoked for account {{userId}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.roles.platform_admin.granted', 'fr', 'EMAIL_BODY', $i18n$Bonjour,

Le rôle d'administrateur de la plateforme TiiBnTick (TNT_ADMIN) a été attribué au compte {{userId}} ({{grantKind}}) par {{grantedBy}}, depuis le tenant {{sourceTenantId}}.

Si cette attribution vous semble anormale, contactez immédiatement le propriétaire du tenant système.

Cordialement,
L'équipe TiiBnTick$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.roles.platform_admin.granted', 'en', 'EMAIL_BODY', $i18n$Hello,

The TiiBnTick platform administrator role (TNT_ADMIN) was granted to account {{userId}} ({{grantKind}}) by {{grantedBy}}, from tenant {{sourceTenantId}}.

If this grant looks wrong to you, contact the owner of the system tenant at once.

Best regards,
The TiiBnTick team$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.roles.platform_admin.revoked', 'fr', 'EMAIL_BODY', $i18n$Bonjour,

Le rôle d'administrateur de la plateforme TiiBnTick (TNT_ADMIN) du compte {{userId}} a été révoqué par {{revokedBy}}.
Motif : {{reason}}

Cordialement,
L'équipe TiiBnTick$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.roles.platform_admin.revoked', 'en', 'EMAIL_BODY', $i18n$Hello,

The TiiBnTick platform administrator role (TNT_ADMIN) of account {{userId}} was revoked by {{revokedBy}}.
Reason: {{reason}}

Best regards,
The TiiBnTick team$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.roles.platform_admin.granted', 'fr', 'IN_APP', $i18n$🛡️ Rôle TNT_ADMIN attribué au compte {{userId}} par {{grantedBy}} (tenant d'obtention {{sourceTenantId}}).$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.roles.platform_admin.granted', 'en', 'IN_APP', $i18n$🛡️ TNT_ADMIN role granted to account {{userId}} by {{grantedBy}} (source tenant {{sourceTenantId}}).$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.roles.platform_admin.revoked', 'fr', 'IN_APP', $i18n$🛡️ Rôle TNT_ADMIN du compte {{userId}} révoqué par {{revokedBy}}. Motif : {{reason}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.roles.platform_admin.revoked', 'en', 'IN_APP', $i18n$🛡️ TNT_ADMIN role of account {{userId}} revoked by {{revokedBy}}. Reason: {{reason}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.roles.platform_admin.granted', 'fr', 'DEFAULT', $i18n$Bonjour,

Le rôle d'administrateur de la plateforme TiiBnTick (TNT_ADMIN) a été attribué au compte {{userId}} ({{grantKind}}) par {{grantedBy}}, depuis le tenant {{sourceTenantId}}.

Si cette attribution vous semble anormale, contactez immédiatement le propriétaire du tenant système.

Cordialement,
L'équipe TiiBnTick$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.roles.platform_admin.granted', 'en', 'DEFAULT', $i18n$Hello,

The TiiBnTick platform administrator role (TNT_ADMIN) was granted to account {{userId}} ({{grantKind}}) by {{grantedBy}}, from tenant {{sourceTenantId}}.

If this grant looks wrong to you, contact the owner of the system tenant at once.

Best regards,
The TiiBnTick team$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.roles.platform_admin.revoked', 'fr', 'DEFAULT', $i18n$Bonjour,

Le rôle d'administrateur de la plateforme TiiBnTick (TNT_ADMIN) du compte {{userId}} a été révoqué par {{revokedBy}}.
Motif : {{reason}}

Cordialement,
L'équipe TiiBnTick$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.roles.platform_admin.revoked', 'en', 'DEFAULT', $i18n$Hello,

The TiiBnTick platform administrator role (TNT_ADMIN) of account {{userId}} was revoked by {{revokedBy}}.
Reason: {{reason}}

Best regards,
The TiiBnTick team$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
