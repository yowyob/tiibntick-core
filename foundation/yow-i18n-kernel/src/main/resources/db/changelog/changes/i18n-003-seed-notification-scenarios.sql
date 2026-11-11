--liquibase formatted sql
--changeset MANFOUO_Braun:i18n-003-seed-notification-scenarios
--comment: Missing notification scenarios, bilingual incident texts written from structured facts, and channel flavours (push, SMS, e-mail subject). Re-runnable: a row is only replaced while it is still the untouched seed (version 1, updated_by 'seed'), so an admin edit is never overwritten.

INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.sub_deliverer.invited', 'fr', 'DEFAULT', $i18n$📩 Vous avez été invité(e) à rejoindre l'organisation {{orgName}} comme sous-livreur. Acceptez ou refusez via l'application.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.sub_deliverer.invited', 'en', 'DEFAULT', $i18n$📩 You have been invited to join {{orgName}} as a sub-deliverer. Accept or decline in the app.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.sub_deliverer.invitation_accepted', 'fr', 'DEFAULT', $i18n$✅ {{subDelivererName}} a accepté votre invitation et rejoint votre organisation.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.sub_deliverer.invitation_accepted', 'en', 'DEFAULT', $i18n$✅ {{subDelivererName}} has accepted your invitation and joined your organization.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.billing.surcharge_triggered', 'fr', 'DEFAULT', $i18n$ℹ️ Un supplément tarifaire a été appliqué à votre livraison ({{deliveryId}}) : {{surchargeName}} = {{surchargeAmount}} XAF.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.billing.surcharge_triggered', 'en', 'DEFAULT', $i18n$ℹ️ A billing surcharge was applied to your delivery ({{deliveryId}}): {{surchargeName}} = {{surchargeAmount}} XAF.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.created', 'fr', 'DEFAULT', $i18n$Nouvel incident {{reference}} — type {{incidentType}}, mission {{missionId}}.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.created', 'en', 'DEFAULT', $i18n$New incident {{reference}} — type {{incidentType}}, mission {{missionId}}.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.escalated', 'fr', 'DEFAULT', $i18n$Incident {{reference}} escaladé (niveau {{level}}) — gravité : {{severity}}.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.escalated', 'en', 'DEFAULT', $i18n$Incident {{reference}} escalated (level {{level}}) — severity: {{severity}}.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.agency.handling.started', 'fr', 'DEFAULT', $i18n$Incident {{reference}} pris en charge par l'agence.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.agency.handling.started', 'en', 'DEFAULT', $i18n$Incident {{reference}} is now handled by the agency.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.handover.confirmed', 'fr', 'DEFAULT', $i18n$Passation confirmée pour l'incident {{reference}} : le transfert du colis est enregistré sur la blockchain.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.handover.confirmed', 'en', 'DEFAULT', $i18n$Handover confirmed for incident {{reference}}: the parcel transfer is recorded on the blockchain.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.driver.mission.assigned', 'fr', 'DEFAULT', $i18n$Mission démarrée : vous êtes désormais le livreur assigné (incident {{reference}}).$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.driver.mission.assigned', 'en', 'DEFAULT', $i18n$Mission started: you are now the assigned driver (incident {{reference}}).$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.driver.proposal', 'fr', 'DEFAULT', $i18n$Nouvelle affectation : un remplacement d'urgence est demandé pour l'incident {{reference}}. Acceptez ou refusez dans l'application.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.driver.proposal', 'en', 'DEFAULT', $i18n$New assignment: an emergency replacement is needed for incident {{reference}}. Accept or decline in the app.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.auto.failed', 'fr', 'DEFAULT', $i18n$Résolution automatique échouée pour l'incident {{reference}} : aucun livreur de remplacement trouvé, une intervention manuelle est requise.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.auto.failed', 'en', 'DEFAULT', $i18n$Automatic resolution failed for incident {{reference}}: no replacement driver found, manual intervention is required.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.interagency.coop.requested', 'fr', 'DEFAULT', $i18n$Assistance inter-agences demandée — type : {{cooperationType}}. Détails : {{details}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.interagency.coop.requested', 'en', 'DEFAULT', $i18n$Inter-agency assistance requested — type: {{cooperationType}}. Details: {{details}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.interagency.coop.updated', 'fr', 'DEFAULT', $i18n$Mise à jour de la coopération inter-agences.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.interagency.coop.updated', 'en', 'DEFAULT', $i18n$Inter-agency cooperation update.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.interagency.coop.accepted', 'fr', 'DEFAULT', $i18n$Votre demande de coopération inter-agences a été acceptée.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.interagency.coop.accepted', 'en', 'DEFAULT', $i18n$Your inter-agency cooperation request was accepted.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.interagency.coop.rejected', 'fr', 'DEFAULT', $i18n$Votre demande de coopération inter-agences a été refusée. Motif : {{reason}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.interagency.coop.rejected', 'en', 'DEFAULT', $i18n$Your inter-agency cooperation request was declined. Reason: {{reason}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.created', 'fr', 'PUSH', $i18n$Incident {{reference}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.created', 'en', 'PUSH', $i18n$Incident {{reference}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.escalated', 'fr', 'PUSH', $i18n$Incident {{reference}} escaladé (niv. {{level}})$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.escalated', 'en', 'PUSH', $i18n$Incident {{reference}} escalated (lvl {{level}})$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.auto.failed', 'fr', 'PUSH', $i18n$Incident {{reference}} : action manuelle requise$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.auto.failed', 'en', 'PUSH', $i18n$Incident {{reference}}: manual action needed$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.package.delivered', 'fr', 'PUSH', $i18n$Colis {{package_id}} livré ✅$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.package.delivered', 'en', 'PUSH', $i18n$Parcel {{package_id}} delivered ✅$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.mission.assigned', 'fr', 'PUSH', $i18n$Nouvelle mission assignée$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.mission.assigned', 'en', 'PUSH', $i18n$New mission assigned$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.package.delivered', 'fr', 'SMS', $i18n$Colis {{package_id}} livré.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.package.delivered', 'en', 'SMS', $i18n$Parcel {{package_id}} delivered.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'account.otp.sent', 'fr', 'SMS', $i18n$TiiBnTick : code {{otp}} ({{duration}} min).$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'account.otp.sent', 'en', 'SMS', $i18n$TiiBnTick: code {{otp}} ({{duration}} min).$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'account.welcome', 'fr', 'EMAIL_SUBJECT', $i18n$Bienvenue sur TiiBnTick$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'account.welcome', 'en', 'EMAIL_SUBJECT', $i18n$Welcome to TiiBnTick$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'account.otp.sent', 'fr', 'EMAIL_SUBJECT', $i18n$Votre code de vérification TiiBnTick$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'account.otp.sent', 'en', 'EMAIL_SUBJECT', $i18n$Your TiiBnTick verification code$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'billing.invoice.generated', 'fr', 'EMAIL_SUBJECT', $i18n$Votre facture TiiBnTick$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'billing.invoice.generated', 'en', 'EMAIL_SUBJECT', $i18n$Your TiiBnTick invoice$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.agency.access_credentials', 'fr', 'EMAIL_SUBJECT', $i18n$Vos accès TiiBnTick Agency$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.agency.access_credentials', 'en', 'EMAIL_SUBJECT', $i18n$Your TiiBnTick Agency access$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.registration_received', 'fr', 'EMAIL_SUBJECT', $i18n$Votre inscription TiiBnTick a bien été reçue$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.registration_received', 'en', 'EMAIL_SUBJECT', $i18n$We received your TiiBnTick registration$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.account_approved', 'fr', 'EMAIL_SUBJECT', $i18n$Votre compte TiiBnTick est approuvé$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.account_approved', 'en', 'EMAIL_SUBJECT', $i18n$Your TiiBnTick account is approved$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.account_rejected', 'fr', 'EMAIL_SUBJECT', $i18n$Votre demande TiiBnTick n'a pas été retenue$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.account_rejected', 'en', 'EMAIL_SUBJECT', $i18n$Your TiiBnTick application was not accepted$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.account_suspended', 'fr', 'EMAIL_SUBJECT', $i18n$Votre compte TiiBnTick est suspendu$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.account_suspended', 'en', 'EMAIL_SUBJECT', $i18n$Your TiiBnTick account is suspended$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.account_revoked', 'fr', 'EMAIL_SUBJECT', $i18n$Votre accès TiiBnTick a été retiré$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.account_revoked', 'en', 'EMAIL_SUBJECT', $i18n$Your TiiBnTick access has been revoked$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, updated_at = now()
WHERE i18n.message.updated_by = 'seed' AND i18n.message.version = 1 AND i18n.message.text IS DISTINCT FROM EXCLUDED.text;

--rollback DELETE FROM i18n.message WHERE updated_by = 'seed' AND msg_key LIKE 'incident.notification.%' AND variant <> 'DEFAULT';
