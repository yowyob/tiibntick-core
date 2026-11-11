--liquibase formatted sql
--changeset MANFOUO_Braun:i18n-004-seed-profile-welcome
--comment: Welcome messages sent when a client / deliverer / freelancer / relay operator profile is created: an on-screen notice (DEFAULT) and an e-mail (subject + body). Re-runnable: only an untouched seed row is replaced, an admin edit is kept.
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.welcome.client', 'fr', 'DEFAULT', $i18n$🎉 Bienvenue {{name}} ! Votre profil client est créé : vous pouvez dès maintenant envoyer et suivre vos colis.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.welcome.client', 'en', 'DEFAULT', $i18n$🎉 Welcome {{name}}! Your client profile has been created: you can now send and track your parcels.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.client', 'fr', 'EMAIL_SUBJECT', $i18n$Bienvenue sur TiiBnTick — votre profil client est créé$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.client', 'en', 'EMAIL_SUBJECT', $i18n$Welcome to TiiBnTick — your client profile is created$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.client', 'fr', 'EMAIL_BODY', $i18n$Bonjour {{name}},

Bienvenue sur TiiBnTick ! Votre profil client a été créé avec succès.

Vous pouvez dès maintenant envoyer vos colis et suivre chaque livraison en temps réel.

L'équipe TiiBnTick$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.client', 'en', 'EMAIL_BODY', $i18n$Hello {{name}},

Welcome to TiiBnTick! Your client profile has been created successfully.

You can now send your parcels and follow every delivery in real time.

The TiiBnTick team$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.client', 'fr', 'DEFAULT', $i18n$🎉 Bienvenue {{name}} ! Votre profil client est créé : vous pouvez dès maintenant envoyer et suivre vos colis.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.client', 'en', 'DEFAULT', $i18n$🎉 Welcome {{name}}! Your client profile has been created: you can now send and track your parcels.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.welcome.deliverer', 'fr', 'DEFAULT', $i18n$🎉 Bienvenue {{name}} ! Votre profil livreur est créé : complétez votre dossier pour commencer à recevoir des missions.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.welcome.deliverer', 'en', 'DEFAULT', $i18n$🎉 Welcome {{name}}! Your deliverer profile has been created: complete your file to start receiving missions.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.deliverer', 'fr', 'EMAIL_SUBJECT', $i18n$Bienvenue sur TiiBnTick — votre profil livreur est créé$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.deliverer', 'en', 'EMAIL_SUBJECT', $i18n$Welcome to TiiBnTick — your deliverer profile is created$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.deliverer', 'fr', 'EMAIL_BODY', $i18n$Bonjour {{name}},

Bienvenue sur TiiBnTick ! Votre profil livreur a été créé avec succès.

Complétez votre dossier et vos informations de disponibilité pour commencer à recevoir des missions.

L'équipe TiiBnTick$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.deliverer', 'en', 'EMAIL_BODY', $i18n$Hello {{name}},

Welcome to TiiBnTick! Your deliverer profile has been created successfully.

Complete your file and availability details to start receiving missions.

The TiiBnTick team$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.deliverer', 'fr', 'DEFAULT', $i18n$🎉 Bienvenue {{name}} ! Votre profil livreur est créé : complétez votre dossier pour commencer à recevoir des missions.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.deliverer', 'en', 'DEFAULT', $i18n$🎉 Welcome {{name}}! Your deliverer profile has been created: complete your file to start receiving missions.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.welcome.freelancer', 'fr', 'DEFAULT', $i18n$🎉 Bienvenue {{name}} ! Votre profil livreur freelance est créé : définissez vos zones et disponibilités pour recevoir des courses.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.welcome.freelancer', 'en', 'DEFAULT', $i18n$🎉 Welcome {{name}}! Your freelance deliverer profile has been created: set your zones and availability to receive deliveries.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.freelancer', 'fr', 'EMAIL_SUBJECT', $i18n$Bienvenue sur TiiBnTick — votre profil livreur freelance est créé$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.freelancer', 'en', 'EMAIL_SUBJECT', $i18n$Welcome to TiiBnTick — your freelance deliverer profile is created$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.freelancer', 'fr', 'EMAIL_BODY', $i18n$Bonjour {{name}},

Bienvenue sur TiiBnTick ! Votre profil livreur freelance a été créé avec succès.

Définissez vos zones de service et vos disponibilités pour recevoir des courses.

L'équipe TiiBnTick$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.freelancer', 'en', 'EMAIL_BODY', $i18n$Hello {{name}},

Welcome to TiiBnTick! Your freelance deliverer profile has been created successfully.

Set your service zones and availability to receive deliveries.

The TiiBnTick team$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.freelancer', 'fr', 'DEFAULT', $i18n$🎉 Bienvenue {{name}} ! Votre profil livreur freelance est créé : définissez vos zones et disponibilités pour recevoir des courses.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.freelancer', 'en', 'DEFAULT', $i18n$🎉 Welcome {{name}}! Your freelance deliverer profile has been created: set your zones and availability to receive deliveries.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.welcome.relay_operator', 'fr', 'DEFAULT', $i18n$🎉 Bienvenue {{name}} ! Votre profil d'opérateur de point relais est créé : renseignez vos horaires et votre capacité.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.welcome.relay_operator', 'en', 'DEFAULT', $i18n$🎉 Welcome {{name}}! Your relay point operator profile has been created: fill in your opening hours and capacity.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.relay_operator', 'fr', 'EMAIL_SUBJECT', $i18n$Bienvenue sur TiiBnTick — votre profil opérateur de point relais est créé$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.relay_operator', 'en', 'EMAIL_SUBJECT', $i18n$Welcome to TiiBnTick — your relay point operator profile is created$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.relay_operator', 'fr', 'EMAIL_BODY', $i18n$Bonjour {{name}},

Bienvenue sur TiiBnTick ! Votre profil opérateur de point relais a été créé avec succès.

Renseignez vos horaires d'ouverture et votre capacité d'accueil de colis.

L'équipe TiiBnTick$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.relay_operator', 'en', 'EMAIL_BODY', $i18n$Hello {{name}},

Welcome to TiiBnTick! Your relay point operator profile has been created successfully.

Fill in your opening hours and parcel capacity.

The TiiBnTick team$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.relay_operator', 'fr', 'DEFAULT', $i18n$🎉 Bienvenue {{name}} ! Votre profil d'opérateur de point relais est créé : renseignez vos horaires et votre capacité.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.profile.created.relay_operator', 'en', 'DEFAULT', $i18n$🎉 Welcome {{name}}! Your relay point operator profile has been created: fill in your opening hours and capacity.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;

--rollback DELETE FROM i18n.message WHERE updated_by = 'seed' AND msg_key LIKE 'notify.profile.%';
