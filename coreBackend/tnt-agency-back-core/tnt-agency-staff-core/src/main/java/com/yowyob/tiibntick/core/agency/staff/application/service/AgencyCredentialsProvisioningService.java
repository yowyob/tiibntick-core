package com.yowyob.tiibntick.core.agency.staff.application.service;

import com.yowyob.kernel.i18n.domain.enums.SupportedLanguage;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.staff.application.port.out.AgencyEmployeeInvitePort;
import com.yowyob.tiibntick.core.agency.staff.domain.AgencyNotificationTemplates;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import com.yowyob.tiibntick.core.roles.application.port.in.AssignTntRoleUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Provisions Kernel login access for agency staff / deliverers and emails temporary credentials.
 */
@Service
public class AgencyCredentialsProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(AgencyCredentialsProvisioningService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%".toCharArray();
    private static final int PASSWORD_LENGTH = 12;

    private final AgencyEmployeeInvitePort employeeInvitePort;
    private final ObjectProvider<AssignTntRoleUseCase> assignTntRoleUseCase;
    private final ObjectProvider<ISendNotificationUseCase> sendNotificationUseCase;
    private final String loginUrl;

    public AgencyCredentialsProvisioningService(
            AgencyEmployeeInvitePort employeeInvitePort,
            ObjectProvider<AssignTntRoleUseCase> assignTntRoleUseCase,
            ObjectProvider<ISendNotificationUseCase> sendNotificationUseCase,
            @Value("${tnt.agency.portal.login-url:}") String loginUrl) {
        this.employeeInvitePort = employeeInvitePort;
        this.assignTntRoleUseCase = assignTntRoleUseCase;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.loginUrl = loginUrl != null ? loginUrl : "";
    }

    public Mono<ProvisionedAccess> provision(ProvisionRequest request) {
        if (request.email() == null || request.email().isBlank() || !request.email().contains("@")) {
            return Mono.error(new TntValidationException(
                    "EMAIL_REQUIRED",
                    "Un email valide est requis pour envoyer les identifiants de connexion.",
                    null));
        }
        if (request.kernelOrganizationId() == null) {
            return Mono.error(new TntValidationException(
                    "KERNEL_ORGANIZATION_REQUIRED",
                    "L'agence n'est pas liée à une organisation Kernel — impossible de créer les identifiants.",
                    null));
        }
        if (request.tntRoleCode() == null || request.tntRoleCode().isBlank()) {
            return Mono.error(new TntValidationException("tntRoleCode is required"));
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String password = generatePassword();
        NameParts name = splitName(request.fullName());

        return employeeInvitePort.invite(new AgencyEmployeeInvitePort.InviteCommand(
                        request.tenantId(),
                        request.kernelOrganizationId(),
                        request.kernelAgencyId(),
                        name.firstName(),
                        name.lastName(),
                        email,
                        password))
                .flatMap(invited -> assignRole(request, invited.userId())
                        .then(sendCredentialsEmail(request, email, password))
                        .thenReturn(new ProvisionedAccess(
                                invited.userId(),
                                invited.actorId() != null ? invited.actorId() : invited.userId(),
                                email,
                                password)));
    }

    private Mono<Void> assignRole(ProvisionRequest request, UUID userId) {
        AssignTntRoleUseCase assign = assignTntRoleUseCase.getIfAvailable();
        if (assign == null) {
            log.warn("[AgencyCredentials] AssignTntRoleUseCase unavailable — skipping role {}",
                    request.tntRoleCode());
            return Mono.empty();
        }
        return assign.assignRole(request.tenantId(), userId, request.tntRoleCode(), request.agencyId())
                .doOnSuccess(r -> log.info("[AgencyCredentials] Role {} assigned to user {}",
                        request.tntRoleCode(), userId))
                .doOnError(e -> log.warn("[AgencyCredentials] Role assignment failed user={} role={}: {}",
                        userId, request.tntRoleCode(), e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private Mono<Void> sendCredentialsEmail(ProvisionRequest request, String email, String password) {
        ISendNotificationUseCase notify = sendNotificationUseCase.getIfAvailable();
        if (notify == null) {
            log.warn("[AgencyCredentials] ISendNotificationUseCase unavailable — credentials email skipped for {}",
                    email);
            return Mono.empty();
        }

        Map<String, Object> params = new HashMap<>();
        params.put("fullName", nullToEmpty(request.fullName()));
        params.put("email", email);
        params.put("password", password);
        params.put("roleLabel", nullToEmpty(request.roleLabel()));
        params.put("loginUrl", loginUrl);

        NotificationModel model = NotificationModel.of(
                AgencyNotificationTemplates.ACCESS_CREDENTIALS,
                SupportedLanguage.FR_CM.getTag(),
                params);

        return notify.send(
                        request.tenantId().toString(),
                        request.kernelOrganizationId() != null
                                ? request.kernelOrganizationId().toString()
                                : null,
                        email,
                        email,
                        model,
                        NotificationChannel.EMAIL)
                .doOnSuccess(n -> log.info("[AgencyCredentials] Credentials email sent to {}", email))
                .doOnError(e -> log.warn("[AgencyCredentials] Credentials email failed for {}: {}",
                        email, e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    static String generatePassword() {
        char[] chars = new char[PASSWORD_LENGTH];
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            chars[i] = PASSWORD_ALPHABET[RANDOM.nextInt(PASSWORD_ALPHABET.length)];
        }
        return new String(chars);
    }

    static NameParts splitName(String fullName) {
        String trimmed = fullName != null ? fullName.trim() : "";
        if (trimmed.isEmpty()) {
            return new NameParts("User", "Agency");
        }
        String[] parts = trimmed.split("\\s+", 2);
        String first = parts[0];
        String last = parts.length > 1 ? parts[1] : parts[0];
        return new NameParts(first, last);
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    public record ProvisionRequest(
            UUID tenantId,
            UUID agencyId,
            UUID kernelOrganizationId,
            UUID kernelAgencyId,
            String fullName,
            String email,
            String tntRoleCode,
            String roleLabel) {
    }

    public record ProvisionedAccess(
            UUID userId,
            UUID actorId,
            String email,
            String temporaryPassword) {
    }

    record NameParts(String firstName, String lastName) {
    }
}
