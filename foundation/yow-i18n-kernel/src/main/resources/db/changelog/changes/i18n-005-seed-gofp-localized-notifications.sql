--liquibase formatted sql
--changeset MANFOUO_Braun:i18n-005-seed-gofp-localized-notifications
--comment: Go-Freelancer notifications written once per language (parameters instead of French literals), so each recipient reads them in their own account language. Also retires the account OTP text: OTP/MFA belongs to the Kernel, the Core no longer sends codes.
DELETE FROM i18n.message WHERE namespace = 'notify' AND msg_key = 'account.otp.sent' AND updated_by = 'seed';

INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.picked_up', 'fr', 'DEFAULT', $i18n$Colis récupéré

Votre colis{{ref}} a été récupéré par le livreur.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.picked_up', 'en', 'DEFAULT', $i18n$Parcel picked up

Your parcel{{ref}} has been picked up by the courier.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.in_transit', 'fr', 'DEFAULT', $i18n$Colis en route

Votre colis{{ref}} est en cours de livraison.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.in_transit', 'en', 'DEFAULT', $i18n$Parcel on its way

Your parcel{{ref}} is being delivered.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.at_relay_point', 'fr', 'DEFAULT', $i18n$Colis au point relais

Votre colis{{ref}} a été déposé dans un point relais.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.at_relay_point', 'en', 'DEFAULT', $i18n$Parcel at a relay point

Your parcel{{ref}} has been dropped at a relay point.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.delivered', 'fr', 'DEFAULT', $i18n$Colis livré

Votre colis{{ref}} a été livré.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.delivered', 'en', 'DEFAULT', $i18n$Parcel delivered

Your parcel{{ref}} has been delivered.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.failed', 'fr', 'DEFAULT', $i18n$Livraison échouée

La livraison du colis{{ref}} n'a pas pu aboutir.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.failed', 'en', 'DEFAULT', $i18n$Delivery failed

The delivery of parcel{{ref}} could not be completed.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.cancelled', 'fr', 'DEFAULT', $i18n$Livraison annulée

La livraison du colis{{ref}} a été annulée.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.cancelled', 'en', 'DEFAULT', $i18n$Delivery cancelled

The delivery of parcel{{ref}} has been cancelled.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.paused', 'fr', 'DEFAULT', $i18n$Livraison suspendue

La livraison du colis{{ref}} est suspendue à cause d'un incident. Nous revenons vers vous dès sa résolution.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.paused', 'en', 'DEFAULT', $i18n$Delivery on hold

The delivery of parcel{{ref}} is on hold because of an incident. We will get back to you as soon as it is resolved.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.late', 'fr', 'DEFAULT', $i18n$Livraison en retard

La livraison du colis{{ref}} dépasse le délai prévu.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery.late', 'en', 'DEFAULT', $i18n$Delivery late

The delivery of parcel{{ref}} is taking longer than planned.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay.parcel_deposited', 'fr', 'DEFAULT', $i18n$Nouveau dépôt

Un nouveau colis a été déposé dans votre point relais « {{relayName}} ». Suivi : {{trackingCode}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay.parcel_deposited', 'en', 'DEFAULT', $i18n$New deposit

A new parcel has been dropped at your relay point “{{relayName}}”. Tracking: {{trackingCode}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay.parcel_arrived', 'fr', 'DEFAULT', $i18n$Colis arrivé !

Votre colis est arrivé au point relais {{relayName}}. Il sera gardé pendant {{storageDays}} jours sans frais de pénalité.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay.parcel_arrived', 'en', 'DEFAULT', $i18n$Parcel arrived!

Your parcel has arrived at relay point {{relayName}}. It will be kept for {{storageDays}} days with no penalty fee.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay.delivery_expected', 'fr', 'DEFAULT', $i18n$Nouvelle livraison prévue !

Un livreur vient d'accepter une course à destination de votre point relais. Préparez-vous à recevoir le colis.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay.delivery_expected', 'en', 'DEFAULT', $i18n$New delivery expected!

A courier has just accepted a delivery to your relay point. Get ready to receive the parcel.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay_status.approved', 'fr', 'DEFAULT', $i18n$Compte Point Relais approuvé

Votre point relais a été approuvé.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay_status.approved', 'en', 'DEFAULT', $i18n$Relay point account approved

Your relay point has been approved.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay_status.rejected', 'fr', 'DEFAULT', $i18n$Compte Point Relais rejeté

Votre point relais a été rejeté.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay_status.rejected', 'en', 'DEFAULT', $i18n$Relay point account rejected

Your relay point has been rejected.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay_status.rejected_reason', 'fr', 'DEFAULT', $i18n$Compte Point Relais rejeté

Votre point relais a été rejeté : {{reason}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay_status.rejected_reason', 'en', 'DEFAULT', $i18n$Relay point account rejected

Your relay point has been rejected: {{reason}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay_status.suspended', 'fr', 'DEFAULT', $i18n$Point Relais suspendu

Votre point relais a été suspendu.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay_status.suspended', 'en', 'DEFAULT', $i18n$Relay point suspended

Your relay point has been suspended.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay_status.revoked', 'fr', 'DEFAULT', $i18n$Point Relais révoqué

Votre point relais a été révoqué.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay_status.revoked', 'en', 'DEFAULT', $i18n$Relay point revoked

Your relay point has been revoked.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay_status.reactivated', 'fr', 'DEFAULT', $i18n$Point Relais activé

Votre point relais est à nouveau actif.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.relay_status.reactivated', 'en', 'DEFAULT', $i18n$Relay point reactivated

Your relay point is active again.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.chat.new_message', 'fr', 'DEFAULT', $i18n$Nouveau message

{{preview}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.chat.new_message', 'en', 'DEFAULT', $i18n$New message

{{preview}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.chat.new_message_from', 'fr', 'DEFAULT', $i18n$Nouveau message de {{sender}}

{{preview}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.chat.new_message_from', 'en', 'DEFAULT', $i18n$New message from {{sender}}

{{preview}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.route.updated', 'fr', 'DEFAULT', $i18n$Itinéraire mis à jour !

Votre itinéraire pour la livraison en cours a été modifié. Raison : {{reason}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.route.updated', 'en', 'DEFAULT', $i18n$Route updated!

Your route for the current delivery has changed. Reason: {{reason}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.route.improved', 'fr', 'DEFAULT', $i18n$Meilleur itinéraire trouvé !

Un itinéraire optimisé a été trouvé pour votre livraison. Économie : {{distanceSavedKm}} km, {{timeSavedMin}} min gagnées.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.route.improved', 'en', 'DEFAULT', $i18n$Better route found!

An optimised route has been found for your delivery. Savings: {{distanceSavedKm}} km, {{timeSavedMin}} min.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.announcement.new_candidates', 'fr', 'DEFAULT', $i18n$Nouveau candidat pour votre annonce

{{count}} livreur(s) ont candidaté pour votre annonce. Classement actuel (meilleur en premier) : {{ranking}}. Consultez la liste pour choisir.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.announcement.new_candidates', 'en', 'DEFAULT', $i18n$New candidate for your announcement

{{count}} courier(s) applied to your announcement. Current ranking (best first): {{ranking}}. Check the list to choose.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.announcement.new_candidates_filtered', 'fr', 'DEFAULT', $i18n$Nouveau candidat pour votre annonce

{{count}} livreur(s) ont candidaté pour votre annonce (filtre véhicule : {{vehicleType}}). Classement actuel (meilleur en premier) : {{ranking}}. Consultez la liste pour choisir.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.announcement.new_candidates_filtered', 'en', 'DEFAULT', $i18n$New candidate for your announcement

{{count}} courier(s) applied to your announcement (vehicle filter: {{vehicleType}}). Current ranking (best first): {{ranking}}. Check the list to choose.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.announcement.available', 'fr', 'DEFAULT', $i18n$Nouvelle course disponible !

Une course correspond à votre position. Cliquez pour voir les détails.

Annonce : {{announcementId}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.announcement.available', 'fr', 'EMAIL_SUBJECT', $i18n$Nouvelle course disponible !$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.announcement.available', 'fr', 'EMAIL_BODY', $i18n$Une course correspond à votre position. Cliquez pour voir les détails.

Annonce : {{announcementId}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.announcement.available', 'en', 'DEFAULT', $i18n$New delivery available!

A delivery matches your position. Tap to see the details.

Announcement: {{announcementId}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.announcement.available', 'en', 'EMAIL_SUBJECT', $i18n$New delivery available!$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.announcement.available', 'en', 'EMAIL_BODY', $i18n$A delivery matches your position. Tap to see the details.

Announcement: {{announcementId}}$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.evaluation.client_pickup', 'fr', 'DEFAULT', $i18n$Colis récupéré — Évaluez le Point Relais !

Vous avez récupéré votre colis au {{relayName}}. Merci de donner votre avis et de noter le point relais.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.evaluation.client_pickup', 'fr', 'EMAIL_SUBJECT', $i18n$Colis récupéré — Évaluez le Point Relais !$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.evaluation.client_pickup', 'fr', 'EMAIL_BODY', $i18n$Vous avez récupéré votre colis au {{relayName}}. Merci de donner votre avis et de noter le point relais.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.evaluation.client_pickup', 'en', 'DEFAULT', $i18n$Parcel collected — rate the relay point!

You collected your parcel at {{relayName}}. Please give your feedback and rate the relay point.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.evaluation.client_pickup', 'en', 'EMAIL_SUBJECT', $i18n$Parcel collected — rate the relay point!$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.evaluation.client_pickup', 'en', 'EMAIL_BODY', $i18n$You collected your parcel at {{relayName}}. Please give your feedback and rate the relay point.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.evaluation.relay_handover', 'fr', 'DEFAULT', $i18n$Retrait effectué — Évaluez le client !

Le colis a été remis au client. Prenez un instant pour évaluer votre interaction.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.evaluation.relay_handover', 'fr', 'EMAIL_SUBJECT', $i18n$Retrait effectué — Évaluez le client !$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.evaluation.relay_handover', 'fr', 'EMAIL_BODY', $i18n$Le colis a été remis au client. Prenez un instant pour évaluer votre interaction.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.evaluation.relay_handover', 'en', 'DEFAULT', $i18n$Pickup completed — rate the customer!

The parcel has been handed to the customer. Take a moment to rate your interaction.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.evaluation.relay_handover', 'en', 'EMAIL_SUBJECT', $i18n$Pickup completed — rate the customer!$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.evaluation.relay_handover', 'en', 'EMAIL_BODY', $i18n$The parcel has been handed to the customer. Take a moment to rate your interaction.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.password_reset', 'fr', 'DEFAULT', $i18n$Réinitialisation de mot de passe

Vous avez demandé une réinitialisation de mot de passe. Veuillez contacter le support.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.password_reset', 'fr', 'EMAIL_SUBJECT', $i18n$Réinitialisation de mot de passe$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.password_reset', 'fr', 'EMAIL_BODY', $i18n$Vous avez demandé une réinitialisation de mot de passe. Veuillez contacter le support.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.password_reset', 'en', 'DEFAULT', $i18n$Password reset

You asked for a password reset. Please contact support.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.password_reset', 'en', 'EMAIL_SUBJECT', $i18n$Password reset$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.password_reset', 'en', 'EMAIL_BODY', $i18n$You asked for a password reset. Please contact support.$i18n$, 1, 'seed')
ON CONFLICT (namespace, msg_key, language, variant) DO NOTHING;
