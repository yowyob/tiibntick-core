package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.yowyob.tiibntick.common.kernel.KernelResponses;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.NotificationDeliveryDto;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.NotificationPreferenceDto;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.NotificationProviderDto;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.NotificationReminderDto;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.NotificationTemplateDto;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.SavePreferenceRequestDto;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.SaveProviderRequestDto;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.SaveReminderRequestDto;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.SaveTemplateRequestDto;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.SendNotificationRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Thin typed client for the Kernel's (RT-comops) generic notification engine
 * ({@code /api/notifications/*} on {@code kernel-core.yowyob.com/kernel-api}).
 *
 * <p>Centralizes the 10 REST calls documented by the Kernel's OpenAPI spec
 * (tag {@code notification-controller}) so every hexagonal port adapter in
 * this module shares the same WebClient wiring, URI paths, and error
 * handling. Individual adapters ({@link KernelDeliveryProviderAdapter},
 * {@link KernelNotificationPreferenceAdapter}, etc.) map between these wire
 * DTOs and tnt-notify-core's own domain vocabulary.
 *
 * <p>Every call requires {@code X-Tenant-Id} and {@code X-Organization-Id}
 * headers — undocumented in the Kernel's OpenAPI spec but enforced by its
 * gateway middleware (confirmed against the real {@code kernel-core.yowyob.com/kernel-api}:
 * missing either header fails closed with {@code TENANT_CONTEXT_REQUIRED} /
 * {@code ORGANIZATION_CONTEXT_REQUIRED} before the request even reaches the
 * notification service).
 *
 * @author MANFOUO Braun
 */
@Component
public class KernelNotificationClient {

    private static final Logger log = LoggerFactory.getLogger(KernelNotificationClient.class);

    private static final String DELIVERIES_PATH = "/api/notifications/deliveries";
    private static final String PREFERENCES_PATH = "/api/notifications/preferences";
    private static final String PREFERENCES_BY_USER_PATH = "/api/notifications/preferences/users/{userId}";
    private static final String PROVIDERS_PATH = "/api/notifications/providers";
    private static final String REMINDERS_PATH = "/api/notifications/reminders";
    private static final String TEMPLATES_PATH = "/api/notifications/templates";

    private final WebClient kernelWebClient;
    private final NotifyProperties properties;

    public KernelNotificationClient(@Qualifier("kernelWebClient") WebClient kernelWebClient,
            NotifyProperties properties) {
        this.kernelWebClient = kernelWebClient;
        this.properties = properties;
    }

    // ── Deliveries ───────────────────────────────────────────────────────────

    public Mono<NotificationDeliveryDto> send(String tenantId, String organizationId,
            SendNotificationRequestDto request) {
        return Mono.defer(() -> {
            var responseSpec = kernelWebClient.post()
                    .uri(DELIVERIES_PATH)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .bodyValue(request)
                    .retrieve();
            return KernelResponses.unwrapObjectOrPropagate(responseSpec, NotificationDeliveryDto.class);
        });
    }

    public Flux<NotificationDeliveryDto> listDeliveries(String tenantId, String organizationId) {
        return Flux.defer(() -> {
            var responseSpec = kernelWebClient.get()
                    .uri(DELIVERIES_PATH)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .retrieve();
            return KernelResponses.unwrapList(responseSpec, NotificationDeliveryDto.class, log, "listDeliveries");
        });
    }

    // ── Preferences ──────────────────────────────────────────────────────────

    public Mono<NotificationPreferenceDto> savePreference(String tenantId, String organizationId,
            SavePreferenceRequestDto request) {
        return Mono.defer(() -> {
            var responseSpec = kernelWebClient.post()
                    .uri(PREFERENCES_PATH)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .bodyValue(request)
                    .retrieve();
            return KernelResponses.unwrapObjectOrPropagate(responseSpec, NotificationPreferenceDto.class);
        });
    }

    public Flux<NotificationPreferenceDto> listPreferences(String tenantId, String organizationId, String userId) {
        return Flux.defer(() -> {
            var responseSpec = kernelWebClient.get()
                    .uri(PREFERENCES_BY_USER_PATH, userId)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .retrieve();
            return KernelResponses.unwrapList(responseSpec, NotificationPreferenceDto.class, log, "listPreferences userId=" + userId);
        });
    }

    // ── Providers ────────────────────────────────────────────────────────────

    public Flux<NotificationProviderDto> listProviders(String tenantId, String organizationId) {
        return Flux.defer(() -> {
            var responseSpec = kernelWebClient.get()
                    .uri(PROVIDERS_PATH)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .retrieve();
            return KernelResponses.unwrapList(responseSpec, NotificationProviderDto.class, log, "listProviders");
        });
    }

    public Mono<NotificationProviderDto> saveProvider(String tenantId, String organizationId,
            SaveProviderRequestDto request) {
        return Mono.defer(() -> {
            var responseSpec = kernelWebClient.post()
                    .uri(PROVIDERS_PATH)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .bodyValue(request)
                    .retrieve();
            return KernelResponses.unwrapObjectOrPropagate(responseSpec, NotificationProviderDto.class);
        });
    }

    // ── Reminders ────────────────────────────────────────────────────────────

    public Flux<NotificationReminderDto> listReminders(String tenantId, String organizationId) {
        return Flux.defer(() -> {
            var responseSpec = kernelWebClient.get()
                    .uri(REMINDERS_PATH)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .retrieve();
            return KernelResponses.unwrapList(responseSpec, NotificationReminderDto.class, log, "listReminders");
        });
    }

    public Mono<NotificationReminderDto> saveReminder(String tenantId, String organizationId,
            SaveReminderRequestDto request) {
        return Mono.defer(() -> {
            var responseSpec = kernelWebClient.post()
                    .uri(REMINDERS_PATH)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .bodyValue(request)
                    .retrieve();
            return KernelResponses.unwrapObjectOrPropagate(responseSpec, NotificationReminderDto.class);
        });
    }

    // ── Templates ────────────────────────────────────────────────────────────

    public Flux<NotificationTemplateDto> listTemplates(String tenantId, String organizationId) {
        return Flux.defer(() -> {
            var responseSpec = kernelWebClient.get()
                    .uri(TEMPLATES_PATH)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .retrieve();
            return KernelResponses.unwrapList(responseSpec, NotificationTemplateDto.class, log, "listTemplates");
        });
    }

    public Mono<NotificationTemplateDto> saveTemplate(String tenantId, String organizationId,
            SaveTemplateRequestDto request) {
        return Mono.defer(() -> {
            var responseSpec = kernelWebClient.post()
                    .uri(TEMPLATES_PATH)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .bodyValue(request)
                    .retrieve();
            return KernelResponses.unwrapObjectOrPropagate(responseSpec, NotificationTemplateDto.class);
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Applies the tenant/organization context the Kernel requires on every call. A value the caller did
     * not provide falls back to the configured platform default ({@code tnt.notify.kernel.default-*});
     * when neither exists the call fails here with an explicit message instead of being rejected by the
     * Kernel with {@code ORGANIZATION_CONTEXT_REQUIRED}.
     */
    private void tenantHeaders(HttpHeaders headers, String tenantId, String organizationId) {
        headers.set("X-Tenant-Id", requireContext("tenant", tenantId, properties.getKernel().getDefaultTenantId()));
        headers.set("X-Organization-Id",
                requireContext("organization", organizationId, properties.getKernel().getDefaultOrganizationId()));
    }

    private static String requireContext(String what, String given, String configuredDefault) {
        if (given != null && !given.isBlank()) {
            return given;
        }
        if (configuredDefault != null && !configuredDefault.isBlank()) {
            return configuredDefault;
        }
        throw new IllegalStateException("No " + what + " for the Kernel notification call and no default configured "
                + "(tnt.notify.kernel.default-" + what + "-id / TNT_NOTIFY_KERNEL_DEFAULT_"
                + what.toUpperCase() + "_ID)");
    }
}
