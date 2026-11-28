--liquibase formatted sql
--changeset MANFOUO_Braun:i18n-006-rename-delivery-vocabulary-keys
--comment: Lot L8 (spec evenements colis/livraison §2.3, single vocabulary): the template keys follow the delivery names. notify.freelancer_org.mission_assigned -> notify.freelancer_org.delivery_assigned, notify.sub_deliverer.mission_assigned -> notify.sub_deliverer.delivery_assigned, notify.gofp.delivery.at_relay_point -> notify.gofp.delivery.at_relay_hub (status AT_RELAY_HUB). An administrator's edit of an old key is carried over to the new key first; the seed texts then fill whatever is still missing (ON CONFLICT DO NOTHING keeps the carried edits); the old keys are removed last. Same texts as the JSON packs.
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
SELECT gen_random_uuid(), m.namespace,
       CASE m.msg_key
           WHEN 'notify.freelancer_org.mission_assigned' THEN 'notify.freelancer_org.delivery_assigned'
           WHEN 'notify.sub_deliverer.mission_assigned' THEN 'notify.sub_deliverer.delivery_assigned'
           ELSE 'notify.gofp.delivery.at_relay_hub' END,
       m.language, m.variant, m.text, m.version, m.updated_by
FROM i18n.message m
WHERE m.namespace = 'notify'
  AND m.msg_key IN ('notify.freelancer_org.mission_assigned', 'notify.sub_deliverer.mission_assigned',
                    'notify.gofp.delivery.at_relay_point')
  AND m.updated_by IS DISTINCT FROM 'seed'
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.freelancer_org.delivery_assigned', 'fr', 'DEFAULT', $i18n$📦 Livraison {{deliveryId}} assignée à votre organisation {{freelancerOrgId}}.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.sub_deliverer.delivery_assigned', 'fr', 'DEFAULT', $i18n$🏍️ Nouvelle livraison {{deliveryId}} assignée pour l'organisation {{orgId}}. Préparez-vous pour le ramassage.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.freelancer_org.delivery_assigned', 'en', 'DEFAULT', $i18n$📦 Delivery {{deliveryId}} assigned to your organization {{freelancerOrgId}}.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.sub_deliverer.delivery_assigned', 'en', 'DEFAULT', $i18n$🏍️ New delivery {{deliveryId}} assigned for organization {{orgId}}. Prepare for pickup.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.at_relay_hub', 'fr', 'DEFAULT', $i18n$Colis au point relais

Votre colis{{ref}} a été déposé dans un point relais.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.at_relay_hub', 'en', 'DEFAULT', $i18n$Parcel at a relay point

Your parcel{{ref}} has been dropped at a relay point.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
DELETE FROM i18n.message WHERE namespace = 'notify'
  AND msg_key IN ('notify.freelancer_org.mission_assigned', 'notify.sub_deliverer.mission_assigned',
                  'notify.gofp.delivery.at_relay_point');
