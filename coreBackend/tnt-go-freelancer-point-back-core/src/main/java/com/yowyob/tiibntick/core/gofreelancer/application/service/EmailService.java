package com.yowyob.tiibntick.core.gofreelancer.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Service for sending transactional emails via Gmail SMTP.
 *
 * <p>Provides methods for sending various notification emails related to
 * delivery person account lifecycle events. Uses Spring's JavaMailSender
 * configured with Gmail SMTP settings.
 *
 * <p>All email methods are fire-and-forget and log errors without throwing.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender emailSender;

    /**
     * Sends a simple email message synchronously.
     *
     * <p>
     * Constructs and sends an email using the configured JavaMailSender.
     * Errors are logged but not propagated to avoid breaking the main flow.
     *
     * @param to      the recipient's email address
     * @param subject the subject of the email
     * @param text    the body text of the email
     */
    public void sendSimpleMessage(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            message.setFrom("noreply@tiibntick.com");
            emailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (MailException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    /**
     * Sends a simple email message reactively.
     *
     * <p>
     * Wraps the blocking email send operation in a Mono that executes
     * on the bounded elastic scheduler to avoid blocking the event loop.
     *
     * @param to      the recipient's email address
     * @param subject the subject of the email
     * @param text    the body text of the email
     * @return a Mono&lt;Void&gt; signaling completion
     */
    public Mono<Void> sendSimpleMessageReactive(String to, String subject, String text) {
        return Mono.fromRunnable(() -> sendSimpleMessage(to, subject, text))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /**
     * Sends a registration received notification email.
     *
     * <p>
     * Informs the delivery person that their registration request
     * has been received and is pending admin validation.
     *
     * @param to the recipient's email address
     */
    public void sendRegistrationReceived(String to) {
        sendSimpleMessage(
                to,
                "TiiBnTick - Inscription reçue",
                "Bonjour,\n\n" +
                        "Votre demande d'inscription en tant que livreur a bien été reçue.\n\n" +
                        "Votre compte est en attente de validation par notre équipe administrative.\n" +
                        "Un email vous sera envoyé lorsque votre demande aura été examinée.\n\n" +
                        "Cordialement,\n" +
                        "L'équipe TiiBnTick");
    }

    /**
     * Sends an account approved notification email.
     *
     * <p>
     * Informs the delivery person that their account has been approved
     * and they can now access the platform.
     *
     * @param to the recipient's email address
     */
    public void sendAccountApproved(String to) {
        sendSimpleMessage(
                to,
                "TiiBnTick - Compte approuvé",
                "Bonjour,\n\n" +
                        "Félicitations ! Votre compte livreur a été approuvé.\n\n" +
                        "Vous pouvez maintenant vous connecter à l'application et commencer à effectuer des livraisons.\n\n"
                        +
                        "Cordialement,\n" +
                        "L'équipe TiiBnTick");
    }

    /**
     * Sends an account rejected notification email.
     *
     * <p>
     * Informs the delivery person that their registration has been rejected,
     * with an optional reason for the decision.
     *
     * @param to     the recipient's email address
     * @param reason optional reason for rejection (may be null or empty)
     */
    public void sendAccountRejected(String to, String reason) {
        String reasonText = (reason != null && !reason.isEmpty())
                ? "\nRaison : " + reason + "\n"
                : "";
        sendSimpleMessage(
                to,
                "TiiBnTick - Demande d'inscription refusée",
                "Bonjour,\n\n" +
                        "Nous avons le regret de vous informer que votre demande d'inscription " +
                        "en tant que livreur n'a pas été approuvée.\n" +
                        reasonText +
                        "\nSi vous pensez qu'il s'agit d'une erreur, veuillez nous contacter.\n\n" +
                        "Cordialement,\n" +
                        "L'équipe TiiBnTick");
    }

    /**
     * Sends an account suspended notification email.
     *
     * <p>
     * Informs the delivery person that their account has been temporarily
     * suspended.
     * Provides contact information for inquiries.
     *
     * @param to the recipient's email address
     */
    public void sendAccountSuspended(String to) {
        sendSimpleMessage(
                to,
                "TiiBnTick - Compte suspendu",
                "Bonjour,\n\n" +
                        "Nous vous informons que votre compte livreur a été temporairement suspendu.\n\n" +
                        "Durant cette période, vous ne pourrez pas accéder aux fonctionnalités de l'application.\n\n" +
                        "Si vous pensez qu'il s'agit d'une erreur ou pour plus d'informations, " +
                        "veuillez contacter notre équipe support.\n\n" +
                        "Cordialement,\n" +
                        "L'équipe TiiBnTick");
    }

    /**
     * Sends an account revoked notification email.
     *
     * <p>
     * Informs the delivery person that their account has been permanently
     * deactivated.
     * This is a final status with no automatic reactivation.
     *
     * @param to the recipient's email address
     */
    public void sendAccountRevoked(String to) {
        sendSimpleMessage(
                to,
                "TiiBnTick - Compte révoqué",
                "Bonjour,\n\n" +
                        "Nous vous informons que votre compte livreur a été définitivement révoqué.\n\n" +
                        "Cette décision est irrévocable et vous ne pourrez plus accéder à l'application.\n\n" +
                        "Si vous pensez qu'il s'agit d'une erreur, veuillez contacter notre équipe support.\n\n" +
                        "Cordialement,\n" +
                        "L'équipe TiiBnTick");
    }

    /**
     * Sends a delivery assignment notification email.
     *
     * <p>
     * Informs the delivery person that they have been assigned to a delivery.
     *
     * @param to                the delivery person's email address
     * @param announcementTitle the title of the assigned announcement
     */
    public void sendDeliveryAssigned(String to, String announcementTitle) {
        sendSimpleMessage(
                to,
                "TiiBnTick - Livraison assignée",
                "Bonjour,\n\n" +
                        "Félicitations ! Vous avez été sélectionné(e) pour effectuer la livraison suivante :\n\n" +
                        "\"" + announcementTitle + "\"\n\n" +
                        "Veuillez vous connecter à l'application pour consulter les détails de la livraison " +
                        "et commencer le processus.\n\n" +
                        "Cordialement,\n" +
                        "L'équipe TiiBnTick");
    }
}
