package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.yowyob.tiibntick.core.notify.application.port.out.ITranslationPort;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.SaveTemplateRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes sure a TiiBnTick message template exists in the Kernel notification engine before it is used.
 *
 * <p>Kernel templates are scoped by tenant and organization, and identified by
 * {@code (code, channel, locale)}. The first time a tenant/organization uses a template, the registrar
 * lists what the Kernel already holds (one call per tenant/organization, then cached) and creates the
 * missing {@code (code, channel, locale)} from the TiiBnTick language pack. Registration is best effort:
 * any failure is logged and swallowed, because the message is still sent with its ready-made body.
 *
 * @author MANFOUO Braun
 */
public class KernelTemplateRegistrar {

    private static final Logger log = LoggerFactory.getLogger(KernelTemplateRegistrar.class);
    private static final String EMAIL_SUBJECT = "TiiBnTick";

    private final KernelNotificationClient client;
    private final ITranslationPort translationPort;
    /** tenant|organization → known "code|channel|locale" triples. */
    private final ConcurrentHashMap<String, Set<String>> known = new ConcurrentHashMap<>();

    public KernelTemplateRegistrar(KernelNotificationClient client, ITranslationPort translationPort) {
        this.client = client;
        this.translationPort = translationPort;
    }

    /**
     * @return completes once the template is known to the Kernel, or immediately when it cannot be
     *         registered (never errors)
     */
    public Mono<Void> ensureRegistered(String tenantId, String organizationId, String code, String kernelChannel,
            String locale) {
        String scope = tenantId + "|" + organizationId;
        String triple = code + "|" + kernelChannel + "|" + locale;
        return knownTemplates(scope, tenantId, organizationId)
                .flatMap(set -> set.contains(triple)
                        ? Mono.<Void>empty()
                        : register(tenantId, organizationId, code, kernelChannel, locale)
                                .doOnSuccess(v -> set.add(triple)))
                .onErrorResume(e -> {
                    log.warn("Kernel template {} ({}, {}) could not be registered: {}", code, kernelChannel, locale,
                            e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Set<String>> knownTemplates(String scope, String tenantId, String organizationId) {
        Set<String> cached = known.get(scope);
        if (cached != null) {
            return Mono.just(cached);
        }
        return client.listTemplates(tenantId, organizationId)
                .map(t -> t.code() + "|" + t.channel() + "|" + t.locale())
                .collect(() -> (Set<String>) ConcurrentHashMap.<String>newKeySet(), Set::add)
                .doOnNext(set -> known.put(scope, set));
    }

    private Mono<Void> register(String tenantId, String organizationId, String code, String kernelChannel,
            String locale) {
        return translationPort.rawTemplate(code, locale)
                .flatMap(pattern -> client.saveTemplate(tenantId, organizationId, new SaveTemplateRequestDto(
                        code, kernelChannel, locale, "EMAIL".equals(kernelChannel) ? EMAIL_SUBJECT : null, pattern,
                        Boolean.TRUE)))
                .doOnNext(t -> log.info("Registered Kernel notification template {} ({}, {})", code, kernelChannel,
                        locale))
                .then();
    }
}
