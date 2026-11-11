--liquibase formatted sql
--changeset MANFOUO_Braun:i18n-002-seed-existing-messages
--comment: Seed the 61 messages that used to live in messages_fr_CM.json / messages_en_CM.json, now as public languages fr and en. ON CONFLICT DO NOTHING so a re-run or an admin edit is never overwritten.

INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'CLIENT_QUOTE_RECEIVED', 'fr', 'DEFAULT', $i18n$Vous avez reçu un devis de {{providerName}} pour votre demande {{quoteRequestId}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'CLIENT_QUOTE_RECEIVED', 'en', 'DEFAULT', $i18n$You received a quote from {{providerName}} for your request {{quoteRequestId}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'LISTING_APPROVED', 'fr', 'DEFAULT', $i18n$Votre annonce {{listingId}} a été approuvée.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'LISTING_APPROVED', 'en', 'DEFAULT', $i18n$Your listing {{listingId}} has been approved.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'LISTING_REJECTED', 'fr', 'DEFAULT', $i18n$Votre annonce {{listingId}} a été rejetée. Raison : {{reason}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'LISTING_REJECTED', 'en', 'DEFAULT', $i18n$Your listing {{listingId}} was rejected. Reason: {{reason}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'ORDER_COMPLETED', 'fr', 'DEFAULT', $i18n$Votre commande {{orderId}} a été finalisée.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'ORDER_COMPLETED', 'en', 'DEFAULT', $i18n$Your order {{orderId}} has been completed.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'ORDER_CONFIRMED', 'fr', 'DEFAULT', $i18n$Votre commande {{orderId}} a été confirmée par {{providerName}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'ORDER_CONFIRMED', 'en', 'DEFAULT', $i18n$Your order {{orderId}} has been confirmed by {{providerName}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'ORDER_PAID', 'fr', 'DEFAULT', $i18n$Paiement reçu pour la commande {{orderId}} : {{amountXaf}} XAF.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'ORDER_PAID', 'en', 'DEFAULT', $i18n$Payment received for order {{orderId}}: {{amountXaf}} XAF.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'PROVIDER_NEW_QUOTE_REQUEST', 'fr', 'DEFAULT', $i18n$Nouvelle demande de devis de {{clientCity}} (réf. {{quoteRequestId}}).$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'PROVIDER_NEW_QUOTE_REQUEST', 'en', 'DEFAULT', $i18n$New quote request from {{clientCity}} (ref. {{quoteRequestId}}).$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'REVIEW_PUBLISHED', 'fr', 'DEFAULT', $i18n$Nouvel avis {{reviewId}} publié : {{rating}}/5.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'REVIEW_PUBLISHED', 'en', 'DEFAULT', $i18n$New review {{reviewId}} published: {{rating}}/5.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'account.otp.sent', 'fr', 'DEFAULT', $i18n$Votre code de vérification est {{otp}}. Valable {{duration}} minutes.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'account.otp.sent', 'en', 'DEFAULT', $i18n$Your verification code is {{otp}}. Valid for {{duration}} minutes.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'account.welcome', 'fr', 'DEFAULT', $i18n$Bienvenue sur TiiBnTick, {{name}} !$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'account.welcome', 'en', 'DEFAULT', $i18n$Welcome to TiiBnTick, {{name}}!$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'billing.cost.estimate', 'fr', 'DEFAULT', $i18n$Le coût estimé de la livraison est de {{amount}} {{currency}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'billing.cost.estimate', 'en', 'DEFAULT', $i18n$The estimated delivery cost is {{amount}} {{currency}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'billing.invoice.generated', 'fr', 'DEFAULT', $i18n$Votre facture de {{amount}} {{currency}} a été générée.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'billing.invoice.generated', 'en', 'DEFAULT', $i18n$Your invoice of {{amount}} {{currency}} has been generated.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'billing.payment.confirmed', 'fr', 'DEFAULT', $i18n$Paiement de {{amount}} {{currency}} confirmé.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'billing.payment.confirmed', 'en', 'DEFAULT', $i18n$Payment of {{amount}} {{currency}} confirmed.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'billing.wallet.credited', 'fr', 'DEFAULT', $i18n$Votre portefeuille a été crédité de {{amount}} {{currency}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'billing.wallet.credited', 'en', 'DEFAULT', $i18n$Your wallet has been credited with {{amount}} {{currency}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'blockchain.delivery.confirmed', 'fr', 'DEFAULT', $i18n$Livraison du colis {{package_id}} enregistrée de façon immuable.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'blockchain.delivery.confirmed', 'en', 'DEFAULT', $i18n$Package {{package_id}} delivery immutably recorded.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'blockchain.deposit.confirmed', 'fr', 'DEFAULT', $i18n$Dépôt du colis {{package_id}} validé sur la blockchain.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'blockchain.deposit.confirmed', 'en', 'DEFAULT', $i18n$Package {{package_id}} deposit validated on blockchain.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'errors', 'error.driver.unavailable', 'fr', 'DEFAULT', $i18n$Aucun livreur disponible dans votre zone pour le moment.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'errors', 'error.driver.unavailable', 'en', 'DEFAULT', $i18n$No driver available in your area at this time.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'errors', 'error.hub.full', 'fr', 'DEFAULT', $i18n$Le hub {{hub_name}} est actuellement plein. Veuillez choisir un autre point.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'errors', 'error.hub.full', 'en', 'DEFAULT', $i18n$Hub {{hub_name}} is currently full. Please select another relay point.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'errors', 'error.payment.failed', 'fr', 'DEFAULT', $i18n$Le paiement de {{amount}} {{currency}} a échoué. Veuillez réessayer.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'errors', 'error.payment.failed', 'en', 'DEFAULT', $i18n$Payment of {{amount}} {{currency}} failed. Please try again.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'errors', 'error.route.unreachable', 'fr', 'DEFAULT', $i18n$Zone de livraison inaccessible en raison des conditions météorologiques.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'errors', 'error.route.unreachable', 'en', 'DEFAULT', $i18n$Delivery zone unreachable due to weather conditions.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.agency.handling.started', 'fr', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.agency.handling.started', 'en', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.driver.mission.assigned', 'fr', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.driver.mission.assigned', 'en', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.handover.confirmed', 'fr', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.handover.confirmed', 'en', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.auto.failed', 'fr', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.auto.failed', 'en', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.created', 'fr', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.created', 'en', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.driver.proposal', 'fr', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.driver.proposal', 'en', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.escalated', 'fr', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.incident.escalated', 'en', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.interagency.coop.requested', 'fr', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.interagency.coop.requested', 'en', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.interagency.coop.updated', 'fr', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'incident.notification.interagency.coop.updated', 'en', 'DEFAULT', $i18n${{title}} — {{body}} (Incident {{incidentId}})$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.delivery.assigned', 'fr', 'DEFAULT', $i18n$Nouvelle mission de livraison disponible pour {{package_id}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.delivery.assigned', 'en', 'DEFAULT', $i18n$New delivery mission available for package {{package_id}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.delivery.cancelled', 'fr', 'DEFAULT', $i18n$La livraison du colis {{package_id}} a été annulée.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.delivery.cancelled', 'en', 'DEFAULT', $i18n$Delivery of package {{package_id}} has been cancelled.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.delivery.rescheduled', 'fr', 'DEFAULT', $i18n$La livraison du colis {{package_id}} a été replanifiée.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.delivery.rescheduled', 'en', 'DEFAULT', $i18n$Delivery of package {{package_id}} has been rescheduled.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.mission.assigned', 'fr', 'DEFAULT', $i18n$Mission assignée avec succès au livreur {{driver_name}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.mission.assigned', 'en', 'DEFAULT', $i18n$Mission successfully assigned to driver {{driver_name}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.package.at_hub', 'fr', 'DEFAULT', $i18n$Votre colis {{package_id}} est disponible au point relais {{hub_name}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.package.at_hub', 'en', 'DEFAULT', $i18n$Your package {{package_id}} is available at relay hub {{hub_name}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.package.delivered', 'fr', 'DEFAULT', $i18n$Le colis {{package_id}} a été livré avec succès.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.package.delivered', 'en', 'DEFAULT', $i18n$Package {{package_id}} has been successfully delivered.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.package.deposit_confirmed', 'fr', 'DEFAULT', $i18n$Dépôt confirmé. Colis {{package_id}} reçu au hub {{hub_name}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.package.deposit_confirmed', 'en', 'DEFAULT', $i18n$Deposit confirmed. Package {{package_id}} received at hub {{hub_name}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.package.in_transit', 'fr', 'DEFAULT', $i18n$Votre colis {{package_id}} est en route. Livreur: {{driver_name}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.package.in_transit', 'en', 'DEFAULT', $i18n$Your package {{package_id}} is on its way. Driver: {{driver_name}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.package.picked_up', 'fr', 'DEFAULT', $i18n$Le colis {{package_id}} a été récupéré par {{driver_name}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notification.package.picked_up', 'en', 'DEFAULT', $i18n$Package {{package_id}} was picked up by {{driver_name}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.agency.access_credentials', 'fr', 'DEFAULT', $i18n$Bonjour {{fullName}},

Un compte TiiBnTick Agency a été créé pour vous ({{roleLabel}}).

Identifiant : {{email}}
Mot de passe temporaire : {{password}}

Connectez-vous : {{loginUrl}}

Changez votre mot de passe après la première connexion.

Cordialement,
L'équipe TiiBnTick$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.agency.access_credentials', 'en', 'DEFAULT', $i18n$Hello {{fullName}},

A TiiBnTick Agency account has been created for you ({{roleLabel}}).

Login: {{email}}
Temporary password: {{password}}

Sign in: {{loginUrl}}

Please change your password after the first login.

Best regards,
The TiiBnTick team$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.billing.template_applied', 'fr', 'DEFAULT', $i18n$💼 Politique tarifaire créée à partir du template {{templateCode}} par {{actorId}}. ID politique : {{policyId}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.billing.template_applied', 'en', 'DEFAULT', $i18n$💼 Billing policy created from template {{templateCode}} by {{actorId}}. Policy ID: {{policyId}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.freelancer_org.kyc_level_upgraded', 'fr', 'DEFAULT', $i18n$✅ Niveau KYC amélioré pour l'organisation {{orgId}} : certifiée niveau {{kycLevel}}. Nouvelles fonctionnalités disponibles.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.freelancer_org.kyc_level_upgraded', 'en', 'DEFAULT', $i18n$✅ KYC level upgraded for organization {{orgId}}: now certified at level {{kycLevel}}. New features unlocked.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.freelancer_org.kyc_rejected', 'fr', 'DEFAULT', $i18n$❌ La vérification KYC de l'organisation {{orgId}} a été rejetée. Raison : {{reason}}. Veuillez soumettre de nouveaux documents.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.freelancer_org.kyc_rejected', 'en', 'DEFAULT', $i18n$❌ KYC verification for organization {{orgId}} was rejected. Reason: {{reason}}. Please submit updated documents.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.freelancer_org.mission_assigned', 'fr', 'DEFAULT', $i18n$📦 Mission assignée à votre organisation {{freelancerOrgId}}. Référence livraison : {{deliveryId}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.freelancer_org.mission_assigned', 'en', 'DEFAULT', $i18n$📦 Mission assigned to your organization {{freelancerOrgId}}. Delivery reference: {{deliveryId}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.freelancer_org.suspended', 'fr', 'DEFAULT', $i18n$⚠️ Votre organisation {{orgId}} a été suspendue. Raison : {{reason}}. Contactez le support pour plus d'informations.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.freelancer_org.suspended', 'en', 'DEFAULT', $i18n$⚠️ Your organization {{orgId}} has been suspended. Reason: {{reason}}. Contact support for more information.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.freelancer_org.unsuspended', 'fr', 'DEFAULT', $i18n$✅ La suspension de votre organisation {{orgId}} a été levée. Vous pouvez reprendre vos activités.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.freelancer_org.unsuspended', 'en', 'DEFAULT', $i18n$✅ The suspension on your organization {{orgId}} has been lifted. You can resume your activities.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.freelancer_org.verified', 'fr', 'DEFAULT', $i18n$🎉 Félicitations ! Votre organisation {{orgId}} a été vérifiée (niveau KYC {{kycLevel}}). Vous pouvez maintenant accepter des missions sur TiiBnTick.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.freelancer_org.verified', 'en', 'DEFAULT', $i18n$🎉 Congratulations! Your organization {{orgId}} has been verified (KYC level {{kycLevel}}). You can now accept missions on TiiBnTick.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.account_approved', 'fr', 'DEFAULT', $i18n$Bonjour,

Félicitations ! Votre compte a été approuvé.

Vous pouvez maintenant vous connecter à l'application.

{{loginUrl}}

Cordialement,
L'équipe TiiBnTick$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.account_approved', 'en', 'DEFAULT', $i18n$Hello,

Congratulations! Your account has been approved.

You can now sign in to the application.

{{loginUrl}}

Best regards,
The TiiBnTick team$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.account_rejected', 'fr', 'DEFAULT', $i18n$Bonjour,

Nous avons le regret de vous informer que votre demande d'inscription n'a pas été approuvée.
{{reason}}

Si vous pensez qu'il s'agit d'une erreur, veuillez nous contacter.

{{loginUrl}}

Cordialement,
L'équipe TiiBnTick$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.account_rejected', 'en', 'DEFAULT', $i18n$Hello,

We regret to inform you that your registration request was not approved.
{{reason}}

If you believe this is an error, please contact us.

{{loginUrl}}

Best regards,
The TiiBnTick team$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.account_revoked', 'fr', 'DEFAULT', $i18n$Bonjour,

Nous vous informons que votre compte a été définitivement révoqué.

Cette décision est irrévocable et vous ne pourrez plus accéder aux fonctionnalités de l'application.

{{loginUrl}}

Cordialement,
L'équipe TiiBnTick$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.account_revoked', 'en', 'DEFAULT', $i18n$Hello,

Your account has been permanently revoked.

This decision is final and you will no longer be able to access the application.

{{loginUrl}}

Best regards,
The TiiBnTick team$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.account_suspended', 'fr', 'DEFAULT', $i18n$Bonjour,

Nous vous informons que votre compte a été temporairement suspendu.

Durant cette période, vous ne pourrez pas accéder aux fonctionnalités de l'application.

{{loginUrl}}

Cordialement,
L'équipe TiiBnTick$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.account_suspended', 'en', 'DEFAULT', $i18n$Hello,

Your account has been temporarily suspended.

During this period, you will not be able to access application features.

{{loginUrl}}

Best regards,
The TiiBnTick team$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery_assigned', 'fr', 'DEFAULT', $i18n$Bonjour,

Félicitations ! Vous avez été sélectionné(e) pour effectuer la livraison suivante :

"{{announcementTitle}}"

Veuillez vous connecter à l'application pour consulter les détails.

Cordialement,
L'équipe TiiBnTick$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.delivery_assigned', 'en', 'DEFAULT', $i18n$Hello,

Congratulations! You have been selected for the following delivery:

"{{announcementTitle}}"

Please sign in to the application for details.

Best regards,
The TiiBnTick team$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.evaluation_request', 'fr', 'DEFAULT', $i18n$Merci de laisser une évaluation pour votre dernière livraison.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.evaluation_request', 'en', 'DEFAULT', $i18n$Please leave a rating for your latest delivery.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.message', 'fr', 'DEFAULT', $i18n${{title}}

{{body}}$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.message', 'en', 'DEFAULT', $i18n${{title}}

{{body}}$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.new_announcement', 'fr', 'DEFAULT', $i18n$Nouvelle course disponible ! Consultez l'application pour les détails.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.new_announcement', 'en', 'DEFAULT', $i18n$New delivery available! Check the app for details.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.registration_received', 'fr', 'DEFAULT', $i18n$Bonjour,

Votre demande d'inscription en tant que livreur a bien été reçue.

Votre compte est en attente de validation par notre équipe administrative.
Un email vous sera envoyé lorsque votre demande aura été examinée.

Cordialement,
L'équipe TiiBnTick$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.gofp.registration_received', 'en', 'DEFAULT', $i18n$Hello,

Your registration request as a delivery person has been received.

Your account is pending validation by our admin team.
You will be notified once your request has been reviewed.

Best regards,
The TiiBnTick team$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.sub_deliverer.mission_assigned', 'fr', 'DEFAULT', $i18n$🏍️ Nouvelle mission {{deliveryId}} assignée pour l'organisation {{orgId}}. Préparez-vous pour le ramassage.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'notify', 'notify.sub_deliverer.mission_assigned', 'en', 'DEFAULT', $i18n$🏍️ New mission {{deliveryId}} assigned for organization {{orgId}}. Prepare for pickup.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'ref', 'status.delivery.completed', 'fr', 'DEFAULT', $i18n$Livraison terminée.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'ref', 'status.delivery.completed', 'en', 'DEFAULT', $i18n$Delivery completed.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'ref', 'status.delivery.in_progress', 'fr', 'DEFAULT', $i18n$Livraison en cours.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'ref', 'status.delivery.in_progress', 'en', 'DEFAULT', $i18n$Delivery in progress.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'ref', 'status.delivery.pending', 'fr', 'DEFAULT', $i18n$En attente d'un livreur.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'ref', 'status.delivery.pending', 'en', 'DEFAULT', $i18n$Waiting for a driver.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'ref', 'status.hub.deposit', 'fr', 'DEFAULT', $i18n$Colis déposé au point relais {{hub_name}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'ref', 'status.hub.deposit', 'en', 'DEFAULT', $i18n$Package dropped off at relay hub {{hub_name}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'ref', 'status.hub.retrieved', 'fr', 'DEFAULT', $i18n$Colis retiré du point relais {{hub_name}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;
INSERT INTO i18n.message (id, namespace, msg_key, language, variant, text, version, updated_by)
VALUES (gen_random_uuid(), 'ref', 'status.hub.retrieved', 'en', 'DEFAULT', $i18n$Package collected from relay hub {{hub_name}}.$i18n$, 1, 'seed') ON CONFLICT DO NOTHING;

INSERT INTO i18n.message_history (message_id, version, text, published_by)
SELECT id, version, text, 'seed' FROM i18n.message WHERE updated_by = 'seed' AND version = 1 ON CONFLICT DO NOTHING;

--rollback DELETE FROM i18n.message WHERE updated_by = 'seed';
