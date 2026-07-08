package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Thin typed client for the Kernel's (RT-comops) generic notification engine
 * ({@code /api/notifications/*} on {@code kernel-core.yowyob.com}).
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
 * gateway middleware (confirmed against the real {@code kernel-core.yowyob.com}:
 * missing either header fails closed with {@code TENANT_CONTEXT_REQUIRED} /
 * {@code ORGANIZATION_CONTEXT_REQUIRED} before the request even reaches the
 * notification service).
 *
 * @author MANFOUO Braun
 */
@Component
public class KernelNotificationClient {

    private static final String DELIVERIES_PATH = "/api/notifications/deliveries";
    private static final String PREFERENCES_PATH = "/api/notifications/preferences";
    private static final String PREFERENCES_BY_USER_PATH = "/api/notifications/preferences/users/{userId}";
    private static final String PROVIDERS_PATH = "/api/notifications/providers";
    private static final String REMINDERS_PATH = "/api/notifications/reminders";
    private static final String TEMPLATES_PATH = "/api/notifications/templates";

    private final WebClient kernelWebClient;

    public KernelNotificationClient(@Qualifier("kernelWebClient") WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    // ── Deliveries ───────────────────────────────────────────────────────────

    public Mono<NotificationDeliveryDto> send(String tenantId, String organizationId,
            SendNotificationRequestDto request) {
        return kernelWebClient.post()
                .uri(DELIVERIES_PATH)
                .headers(h -> tenantHeaders(h, tenantId, organizationId))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationDeliveryDto.class);
    }

    public Flux<NotificationDeliveryDto> listDeliveries(String tenantId, String organizationId) {
        return kernelWebClient.get()
                .uri(DELIVERIES_PATH)
                .headers(h -> tenantHeaders(h, tenantId, organizationId))
                .retrieve()
                .bodyToFlux(NotificationDeliveryDto.class);
    }

    // ── Preferences ──────────────────────────────────────────────────────────

    public Mono<NotificationPreferenceDto> savePreference(String tenantId, String organizationId,
            SavePreferenceRequestDto request) {
        return kernelWebClient.post()
                .uri(PREFERENCES_PATH)
                .headers(h -> tenantHeaders(h, tenantId, organizationId))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationPreferenceDto.class);
    }

    public Flux<NotificationPreferenceDto> listPreferences(String tenantId, String organizationId, String userId) {
        return kernelWebClient.get()
                .uri(PREFERENCES_BY_USER_PATH, userId)
                .headers(h -> tenantHeaders(h, tenantId, organizationId))
                .retrieve()
                .bodyToFlux(NotificationPreferenceDto.class);
    }

    // ── Providers ────────────────────────────────────────────────────────────

    public Flux<NotificationProviderDto> listProviders(String tenantId, String organizationId) {
        return kernelWebClient.get()
                .uri(PROVIDERS_PATH)
                .headers(h -> tenantHeaders(h, tenantId, organizationId))
                .retrieve()
                .bodyToFlux(NotificationProviderDto.class);
    }

    public Mono<NotificationProviderDto> saveProvider(String tenantId, String organizationId,
            SaveProviderRequestDto request) {
        return kernelWebClient.post()
                .uri(PROVIDERS_PATH)
                .headers(h -> tenantHeaders(h, tenantId, organizationId))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationProviderDto.class);
    }

    // ── Reminders ────────────────────────────────────────────────────────────

    public Flux<NotificationReminderDto> listReminders(String tenantId, String organizationId) {
        return kernelWebClient.get()
                .uri(REMINDERS_PATH)
                .headers(h -> tenantHeaders(h, tenantId, organizationId))
                .retrieve()
                .bodyToFlux(NotificationReminderDto.class);
    }

    public Mono<NotificationReminderDto> saveReminder(String tenantId, String organizationId,
            SaveReminderRequestDto request) {
        return kernelWebClient.post()
                .uri(REMINDERS_PATH)
                .headers(h -> tenantHeaders(h, tenantId, organizationId))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationReminderDto.class);
    }

    // ── Templates ────────────────────────────────────────────────────────────

    public Flux<NotificationTemplateDto> listTemplates(String tenantId, String organizationId) {
        return kernelWebClient.get()
                .uri(TEMPLATES_PATH)
                .headers(h -> tenantHeaders(h, tenantId, organizationId))
                .retrieve()
                .bodyToFlux(NotificationTemplateDto.class);
    }

    public Mono<NotificationTemplateDto> saveTemplate(String tenantId, String organizationId,
            SaveTemplateRequestDto request) {
        return kernelWebClient.post()
                .uri(TEMPLATES_PATH)
                .headers(h -> tenantHeaders(h, tenantId, organizationId))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationTemplateDto.class);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void tenantHeaders(HttpHeaders headers, String tenantId, String organizationId) {
        if (tenantId != null) {
            headers.set("X-Tenant-Id", tenantId);
        }
        if (organizationId != null) {
            headers.set("X-Organization-Id", organizationId);
        }
    }
}
