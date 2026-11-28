package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.i18n.adapter.json.JsonLocalePackAdapter;
import com.yowyob.kernel.i18n.application.service.TranslationService;
import com.yowyob.kernel.i18n.config.I18nKernelProperties;
import com.yowyob.tiibntick.core.i18n.application.port.out.UserLanguagePreferenceRepository;
import com.yowyob.tiibntick.core.i18n.application.service.UserLanguageService;
import com.yowyob.tiibntick.core.i18n.domain.model.UserLanguagePreference;
import com.yowyob.tiibntick.core.notify.application.port.out.IPublishNotificationEventPort;
import com.yowyob.tiibntick.core.notify.application.service.NotificationService;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import com.yowyob.tiibntick.core.notify.domain.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.i18n.AccountRecipientLanguageAdapter;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.i18n.I18nTranslationPortAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live end-to-end check against the <b>real</b> Kernel: a real e-mail is sent to a real account, once in French
 * and once in English, through the production classes of the Core (i18n packs, per-account language service,
 * {@link NotificationService}, {@link KernelDeliveryAdapter}, {@link KernelNotificationClient}). Only the
 * persistence of the language preference is replaced by an in-memory map (there is no database here).
 *
 * <p>It sends real mail, so it runs only when {@code TNT_LIVE_KERNEL_API_KEY} is set, e.g.:
 * <pre>
 * TNT_LIVE_KERNEL_API_KEY=... TNT_LIVE_KERNEL_TENANT_ID=... TNT_LIVE_KERNEL_ORGANIZATION_ID=...
 * TNT_LIVE_RECIPIENT_USER_ID=... TNT_LIVE_RECIPIENT_EMAIL=... [TNT_LIVE_KERNEL_BEARER=...]
 * mvn -o verify -Pintegration-tests -pl logistics/tnt-notify-core -am
 *     -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false
 *     -Dit.test=LiveKernelNotificationLanguagesIT -Dfailsafe.failIfNoSpecifiedTests=false
 * </pre>
 * Secrets are read from the environment only and never written anywhere.
 */
class LiveKernelNotificationLanguagesIT {

    private static final String BASE_URL = env("TNT_LIVE_KERNEL_BASE_URL",
            "https://kernel-core.yowyob.com/kernel-api");

    private static String apiKey;
    private static String clientId;
    private static String tenantId;
    private static String organizationId;
    private static String recipientUserId;
    private static String recipientEmail;

    @BeforeAll
    static void requireLiveConfiguration() {
        apiKey = System.getenv("TNT_LIVE_KERNEL_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "TNT_LIVE_KERNEL_API_KEY is not set: this test sends real e-mail and is skipped");
        clientId = env("TNT_LIVE_KERNEL_CLIENT_ID", "tibntick-backend");
        tenantId = require("TNT_LIVE_KERNEL_TENANT_ID");
        organizationId = require("TNT_LIVE_KERNEL_ORGANIZATION_ID");
        recipientUserId = require("TNT_LIVE_RECIPIENT_USER_ID");
        recipientEmail = require("TNT_LIVE_RECIPIENT_EMAIL");
    }

    @Test
    @DisplayName("a real welcome e-mail is delivered in French, then in English after the account language changes")
    void sendsInTheLanguageOfTheAccount() {
        KernelNotificationClient client = kernelClient();
        // The very same wiring the Core uses: packs → catalog translation → account language → Kernel delivery.
        var translation = new I18nTranslationPortAdapter(
                new TranslationService(new JsonLocalePackAdapter(new ObjectMapper(), "fr_CM", "en_CM")),
                new I18nKernelProperties(), new SimpleMeterRegistry());
        InMemoryLanguages store = new InMemoryLanguages();
        UserLanguageService languages = new UserLanguageService(store, Duration.ofSeconds(1));
        KernelDeliveryAdapter delivery = new KernelDeliveryAdapter(client,
                new KernelTemplateRegistrar(client, translation), false);
        NotificationService service = new NotificationService(translation, delivery, new NoopEvents(),
                new AccountRecipientLanguageAdapter(languages));

        UUID user = UUID.fromString(recipientUserId);
        NotificationModel model = new NotificationModel("account.welcome", "fr",
                Map.of("name", "Awa"), NotificationPriority.NORMAL);

        languages.choose(user, "fr").block();
        Notification french = service.send(tenantId, organizationId, recipientUserId, recipientEmail, model,
                NotificationChannel.EMAIL).block();
        System.out.println("LIVE FR → " + describe(french));

        languages.choose(user, "en").block();
        // The preference is cached per account for a short time, exactly as in production.
        Notification english = service.send(tenantId, organizationId, recipientUserId, recipientEmail, model,
                NotificationChannel.EMAIL).block();
        System.out.println("LIVE EN → " + describe(english));

        assertThat(french).isNotNull();
        assertThat(french.getStatus()).as("French delivery: %s", french.getErrorMessage())
                .isNotEqualTo(DeliveryStatus.FAILED);
        assertThat(french.getContent()).isEqualTo("Bienvenue sur TiiBnTick, Awa !");

        assertThat(english).isNotNull();
        assertThat(english.getStatus()).as("English delivery: %s", english.getErrorMessage())
                .isNotEqualTo(DeliveryStatus.FAILED);
        assertThat(english.getContent()).isEqualTo("Welcome to TiiBnTick, Awa!");

        // What the Kernel really recorded for these two deliveries.
        var recorded = client.listDeliveries(tenantId, organizationId).collectList().block();
        assertThat(recorded).isNotNull();
        for (Notification n : List.of(french, english)) {
            var d = recorded.stream().filter(x -> x.id() != null && x.id().toString().equals(n.getId().value()))
                    .findFirst();
            assertThat(d).as("delivery %s present in the Kernel history", n.getId().value()).isPresent();
            System.out.println("KERNEL " + d.get().id() + " status=" + d.get().status() + " channel="
                    + d.get().channel() + " to=" + d.get().recipientAddress() + " body=" + d.get().body());
            assertThat(d.get().body()).isEqualTo(n.getContent());
        }
    }

    private static KernelNotificationClient kernelClient() {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("X-Api-Key", apiKey)
                .defaultHeader("X-Client-Id", clientId)
                .defaultHeader("X-Solution-Code", "TNT");
        // The Core's own kernelWebClient sends no user token. Set TNT_LIVE_KERNEL_BEARER to compare with a
        // call made on behalf of a signed-in user (the Kernel answers 403 to the token-less form).
        String bearer = System.getenv("TNT_LIVE_KERNEL_BEARER");
        if (bearer != null && !bearer.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + bearer);
        }
        WebClient webClient = builder.build();
        NotifyProperties properties = new NotifyProperties();
        properties.getKernel().setDefaultTenantId(tenantId);
        properties.getKernel().setDefaultOrganizationId(organizationId);
        return new KernelNotificationClient(webClient, properties);
    }

    private static String describe(Notification n) {
        return n == null ? "null" : "id=" + n.getId().value() + " status=" + n.getStatus() + " content=\""
                + n.getContent() + "\" error=" + n.getErrorMessage();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String require(String name) {
        String value = System.getenv(name);
        Assumptions.assumeTrue(value != null && !value.isBlank(), name + " is not set");
        return value;
    }

    /** Stands in for the R2DBC table: the language service itself is the production one. */
    private static final class InMemoryLanguages implements UserLanguagePreferenceRepository {
        private final Map<UUID, UserLanguagePreference> rows = new ConcurrentHashMap<>();

        @Override
        public Mono<UserLanguagePreference> findByUserId(UUID userId) {
            return Mono.justOrEmpty(rows.get(userId));
        }

        @Override
        public Mono<UserLanguagePreference> upsert(UUID userId, String language, String source) {
            UserLanguagePreference p = new UserLanguagePreference(userId, language, source, Instant.now());
            rows.put(userId, p);
            return Mono.just(p);
        }

        @Override
        public Mono<UserLanguagePreference> insertIfAbsent(UUID userId, String language, String source) {
            return Mono.just(rows.computeIfAbsent(userId,
                    id -> new UserLanguagePreference(id, language, source, Instant.now())));
        }
    }

    private static final class NoopEvents implements IPublishNotificationEventPort {
        @Override
        public Mono<Void> publishNotificationSent(Notification notification) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> publishNotificationFailed(Notification notification) {
            return Mono.empty();
        }
    }
}
