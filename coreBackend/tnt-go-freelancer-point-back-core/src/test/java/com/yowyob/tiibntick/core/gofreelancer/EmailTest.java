package com.yowyob.tiibntick.core.gofreelancer;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

public class EmailTest {

    @Test
    public void testSendEmail() {
        System.out.println("\n--- Début du test d'envoi d'email ---");
        try {
            String username = "";
            String password = "";
            List<String> lines = Files.readAllLines(Paths.get(".env"));
            for (String line : lines) {
                if (line.startsWith("MAIL_USERNAME=")) username = line.substring("MAIL_USERNAME=".length()).trim();
                if (line.startsWith("MAIL_PASSWORD=")) password = line.substring("MAIL_PASSWORD=".length()).trim();
            }

            if (password.isEmpty()) {
                System.err.println("❌ ERREUR : Le champ MAIL_PASSWORD est vide dans votre fichier .env !");
                System.err.println("Veuillez le remplir et relancer le test.");
                throw new RuntimeException("Mot de passe manquant");
            }

            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost("smtp.gmail.com");
            mailSender.setPort(587);
            mailSender.setUsername(username);
            mailSender.setPassword(password);
            
            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(username);
            message.setTo("isidoratanga@gmail.com");
            message.setSubject("Test d'envoi TiiBnTick");
            message.setText("félicité");
            
            System.out.println("Tentative de connexion avec l'utilisateur: " + username);
            mailSender.send(message);
            System.out.println("✅ Succès : Email envoyé à isidoratanga@gmail.com !\n");
            
        } catch (Exception e) {
            System.err.println("❌ Échec : Impossible d'envoyer l'email.");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
