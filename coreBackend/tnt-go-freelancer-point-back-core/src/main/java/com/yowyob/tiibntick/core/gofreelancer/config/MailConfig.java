package com.yowyob.tiibntick.core.gofreelancer.config;

import org.springframework.context.annotation.Configuration;

/**
 * Mail configuration for Gmail SMTP.
 *
 * <p>Spring Boot auto-configures JavaMailSender using the spring.mail.*
 * properties defined in application.properties or environment variables.
 *
 * <p>Required configuration for Gmail SMTP:
 * <ul>
 *   <li>spring.mail.host=smtp.gmail.com</li>
 *   <li>spring.mail.port=587</li>
 *   <li>spring.mail.username=your-email@gmail.com</li>
 *   <li>spring.mail.password=your-app-password</li>
 *   <li>spring.mail.properties.mail.smtp.auth=true</li>
 *   <li>spring.mail.properties.mail.smtp.starttls.enable=true</li>
 * </ul>
 *
 * <p>Note: For Gmail, you must use an App Password, not your regular password.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Configuration
public class MailConfig {
    // JavaMailSender is auto-configured by Spring Boot Mail Starter
    // No additional bean configuration required for Gmail SMTP
}
