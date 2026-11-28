package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

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
    private static final String INBOX_PATH = "/api/notifications/inbox";
    private static final String INBOX_READ_PATH = "/api/notifications/inbox/{deliveryId}/read";

    private final WebClient kernelWebClient;
    private final NotifyProperties properties;

    public KernelNotificationClient(@Qualifier("kernelWebClient") WebClient kernelWebClient,
            NotifyProperties properties) {
        this.kernelWebClient = kernelWebClient;
        this.properties = properties;
    }

    /**
     * In-app inbox (lot C-36 moved to the Kernel, decision D-157). The Kernel inbox is per user: it is read and
     * marked with the caller's own token ({@code kernelUserWebClient}, which relays the request's bearer). An
     * in-app message is written for another user (the sender is told a courier applied, ...), often outside any
     * request, so it is sent with the platform service account ({@code kernelServiceAccountWebClient}). Both are
     * optional beans of the application: without them the inbox calls fail explicitly.
     */
    private WebClient kernelUserWebClient;
    private WebClient kernelServiceAccountWebClient;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setKernelUserWebClient(@Qualifier("kernelUserWebClient") WebClient kernelUserWebClient) {
        this.kernelUserWebClient = kernelUserWebClient;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setKernelServiceAccountWebClient(
            @Qualifier("kernelServiceAccountWebClient") WebClient kernelServiceAccountWebClient) {
        this.kernelServiceAccountWebClient = kernelServiceAccountWebClient;
    }

    // ── In-app inbox (channel IN_APP) ────────────────────────────────────────

    /** Writes one message into {@code request.recipientUserId}'s Kernel inbox (channel {@code IN_APP}). */
    public Mono<NotificationDeliveryDto> sendInApp(SendNotificationRequestDto request) {
        return Mono.defer(() -> {
            WebClient web = require(kernelServiceAccountWebClient, "kernelServiceAccountWebClient");
            var responseSpec = web.post()
                    .uri(DELIVERIES_PATH)
                    .headers(h -> tenantHeaders(h, null, null))
                    .bodyValue(request)
                    .retrieve();
            return bodyOrPropagate(responseSpec, NotificationDeliveryDto.class);
        });
    }

    /** The caller's Kernel inbox, optionally limited to one application. Errors propagate (no silent empty inbox). */
    public Flux<NotificationDeliveryDto> inbox(String application) {
        return Flux.defer(() -> {
            WebClient web = require(kernelUserWebClient, "kernelUserWebClient");
            return web.get()
                    .uri(b -> {
                        b.path(INBOX_PATH);
                        if (application != null && !application.isBlank()) b.queryParam("application", application);
                        return b.build();
                    })
                    .headers(h -> tenantHeaders(h, null, null))
                    .retrieve()
                    .bodyToFlux(NotificationDeliveryDto.class);
        });
    }

    /** Marks one message of the caller's Kernel inbox as read; emits the message as the Kernel now holds it. */
    public Mono<NotificationDeliveryDto> markInboxRead(UUID deliveryId) {
        return Mono.defer(() -> {
            WebClient web = require(kernelUserWebClient, "kernelUserWebClient");
            var responseSpec = web.post()
                    .uri(INBOX_READ_PATH, deliveryId)
                    .headers(h -> tenantHeaders(h, null, null))
                    .retrieve();
            return bodyOrPropagate(responseSpec, NotificationDeliveryDto.class);
        });
    }

    private static WebClient require(WebClient web, String name) {
        if (web == null) {
            throw new KernelContextMissingException("No " + name + " bean: the Kernel in-app inbox is unavailable");
        }
        return web;
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
            return bodyOrPropagate(responseSpec, NotificationDeliveryDto.class);
        });
    }

    public Flux<NotificationDeliveryDto> listDeliveries(String tenantId, String organizationId) {
        return Flux.defer(() -> {
            var responseSpec = kernelWebClient.get()
                    .uri(DELIVERIES_PATH)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .retrieve();
            return bodyListFailOpen(responseSpec, NotificationDeliveryDto.class, "listDeliveries");
        })
                .onErrorResume(KernelContextMissingException.class, ex -> failOpen(ex));
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
            return bodyOrPropagate(responseSpec, NotificationPreferenceDto.class);
        });
    }

    public Flux<NotificationPreferenceDto> listPreferences(String tenantId, String organizationId, String userId) {
        return Flux.defer(() -> {
            var responseSpec = kernelWebClient.get()
                    .uri(PREFERENCES_BY_USER_PATH, userId)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .retrieve();
            return bodyListFailOpen(responseSpec, NotificationPreferenceDto.class, "listPreferences userId=" + userId);
        })
                .onErrorResume(KernelContextMissingException.class, ex -> failOpen(ex));
    }

    // ── Providers ────────────────────────────────────────────────────────────

    public Flux<NotificationProviderDto> listProviders(String tenantId, String organizationId) {
        return Flux.defer(() -> {
            var responseSpec = kernelWebClient.get()
                    .uri(PROVIDERS_PATH)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .retrieve();
            return bodyListFailOpen(responseSpec, NotificationProviderDto.class, "listProviders");
        })
                .onErrorResume(KernelContextMissingException.class, ex -> failOpen(ex));
    }

    public Mono<NotificationProviderDto> saveProvider(String tenantId, String organizationId,
            SaveProviderRequestDto request) {
        return Mono.defer(() -> {
            var responseSpec = kernelWebClient.post()
                    .uri(PROVIDERS_PATH)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .bodyValue(request)
                    .retrieve();
            return bodyOrPropagate(responseSpec, NotificationProviderDto.class);
        });
    }

    // ── Reminders ────────────────────────────────────────────────────────────

    public Flux<NotificationReminderDto> listReminders(String tenantId, String organizationId) {
        return Flux.defer(() -> {
            var responseSpec = kernelWebClient.get()
                    .uri(REMINDERS_PATH)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .retrieve();
            return bodyListFailOpen(responseSpec, NotificationReminderDto.class, "listReminders");
        })
                .onErrorResume(KernelContextMissingException.class, ex -> failOpen(ex));
    }

    public Mono<NotificationReminderDto> saveReminder(String tenantId, String organizationId,
            SaveReminderRequestDto request) {
        return Mono.defer(() -> {
            var responseSpec = kernelWebClient.post()
                    .uri(REMINDERS_PATH)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .bodyValue(request)
                    .retrieve();
            return bodyOrPropagate(responseSpec, NotificationReminderDto.class);
        });
    }

    // ── Templates ────────────────────────────────────────────────────────────

    public Flux<NotificationTemplateDto> listTemplates(String tenantId, String organizationId) {
        return Flux.defer(() -> {
            var responseSpec = kernelWebClient.get()
                    .uri(TEMPLATES_PATH)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .retrieve();
            return bodyListFailOpen(responseSpec, NotificationTemplateDto.class, "listTemplates");
        })
                .onErrorResume(KernelContextMissingException.class, ex -> failOpen(ex));
    }

    public Mono<NotificationTemplateDto> saveTemplate(String tenantId, String organizationId,
            SaveTemplateRequestDto request) {
        return Mono.defer(() -> {
            var responseSpec = kernelWebClient.post()
                    .uri(TEMPLATES_PATH)
                    .headers(h -> tenantHeaders(h, tenantId, organizationId))
                    .bodyValue(request)
                    .retrieve();
            return bodyOrPropagate(responseSpec, NotificationTemplateDto.class);
        });
    }

    // ── Wire format ──────────────────────────────────────────────────────────

    /**
     * The Kernel notification engine answers with the bare object or array ({@code NotificationDelivery},
     * {@code NotificationDelivery[]}, ...), not with the {@code {success, data, ...}} envelope the other
     * Kernel services use (see the 200 responses of {@code /api/notifications/*} in the OpenAPI spec). Unwrapping
     * an envelope here made every send fail to decode and every read come back empty.
     */
    private static <T> Mono<T> bodyOrPropagate(WebClient.ResponseSpec responseSpec, Class<T> type) {
        return responseSpec.bodyToMono(type);
    }

    /** Fail-open list read: a missing resource or any Kernel error yields an empty list, as for the other Kernel reads. */
    private static <T> Flux<T> bodyListFailOpen(WebClient.ResponseSpec responseSpec, Class<T> type, String description) {
        return responseSpec.bodyToFlux(type)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    log.debug("{}: not found in Kernel", description);
                    return Flux.empty();
                })
                .onErrorResume(Exception.class, ex -> {
                    log.warn("{}: Kernel bridge error: {}", description, ex.getMessage());
                    return Flux.empty();
                });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static final long MISSING_CONTEXT_WARN_EVERY_MS = 60_000;
    private final java.util.concurrent.atomic.AtomicLong lastMissingContextWarn =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * Reads follow the fail-open contract of of the Kernel reads (a Kernel that cannot answer yields an
     * empty list): a deployment without the default tenant/organization must not turn every history poll into
     * an error. Writes ({@code send}, {@code save*}) still fail explicitly. Warned at most once a minute, since
     * the front polls.
     */
    private <T> Flux<T> failOpen(KernelContextMissingException ex) {
        long now = System.currentTimeMillis();
        long last = lastMissingContextWarn.get();
        if (now - last >= MISSING_CONTEXT_WARN_EVERY_MS && lastMissingContextWarn.compareAndSet(last, now)) {
            log.warn("Kernel notification read skipped, returning an empty list: {}", ex.getMessage());
        }
        return Flux.empty();
    }

    /**
     * Applies the tenant/organization context the Kernel requires on every call.
     *
     * <p>An organization belongs to exactly one tenant, so the two are resolved <b>as a pair</b>. When the
     * caller names an organization, its tenant (or the default one) goes with it. When the caller names
     * none — the usual case: a business module only knows its own tenant — the platform notification
     * organization is used together with <i>its own</i> tenant: sending the business tenant next to the
     * notification organization would be refused by the Kernel as a mismatch.
     * When nothing is configured the call fails here with an explicit message instead of being rejected by
     * the Kernel with {@code ORGANIZATION_CONTEXT_REQUIRED}.
     */
    private void tenantHeaders(HttpHeaders headers, String tenantId, String organizationId) {
        boolean organizationGiven = organizationId != null && !organizationId.isBlank();
        String defaultOrganization = properties.getKernel().getDefaultOrganizationId();
        boolean usesDefaultOrganization = !organizationGiven
                && defaultOrganization != null && !defaultOrganization.isBlank();
        String tenant = usesDefaultOrganization
                ? requireContext("tenant", properties.getKernel().getDefaultTenantId(), tenantId)
                : requireContext("tenant", tenantId, properties.getKernel().getDefaultTenantId());
        headers.set("X-Tenant-Id", tenant);
        headers.set("X-Organization-Id", requireContext("organization", organizationId, defaultOrganization));
    }

    private static String requireContext(String what, String given, String configuredDefault) {
        if (given != null && !given.isBlank()) {
            return given;
        }
        if (configuredDefault != null && !configuredDefault.isBlank()) {
            return configuredDefault;
        }
        throw new KernelContextMissingException("No " + what + " for the Kernel notification call and no default configured "
                + "(tnt.notify.kernel.default-" + what + "-id / TNT_NOTIFY_KERNEL_DEFAULT_"
                + what.toUpperCase() + "_ID)");
    }
}
