package com.yowyob.tiibntick.common.kernel;

import org.slf4j.Logger;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Central helper for talking to the Kernel over {@code kernelWebClient}: unwraps the
 * universal {@link KernelEnvelope} and applies the fail-open error contract every
 * {@code Kernel*Adapter} in this codebase is meant to follow (404/any error → empty,
 * never propagated — Kernel unavailability must never block a TiiBnTick write).
 *
 * <p>Every module's Kernel adapter should call {@code kernelWebClient} through this
 * class rather than hand-rolling {@code .bodyToMono(SomeDto.class)} directly — that
 * pattern silently ignores the envelope and always yields a default-valued object.
 * See ADR-012.
 *
 * @author MANFOUO Braun
 */
public final class KernelResponses {

    private KernelResponses() {
    }

    /**
     * Unwraps a single-object Kernel response ({@code ApiResponseXxx} → {@code data: Xxx}),
     * fail-open: 404 or any error resolves to {@link Mono#empty()}.
     *
     * @param responseSpec the in-flight {@code kernelWebClient...retrieve()} call
     * @param dataType     the {@code data} payload's target type
     * @param log          caller's logger, used for the not-found/error messages below
     * @param description  short human-readable description of the call, for log context
     */
    public static <T> Mono<T> unwrapObject(WebClient.ResponseSpec responseSpec,
                                            Class<T> dataType,
                                            Logger log,
                                            String description) {
        ResolvableType envelopeType = ResolvableType.forClassWithGenerics(KernelEnvelope.class, dataType);
        ParameterizedTypeReference<KernelEnvelope<T>> typeRef = ParameterizedTypeReference.forType(envelopeType.getType());
        return responseSpec.bodyToMono(typeRef)
                .flatMap(envelope -> envelope.success() && envelope.data() != null
                        ? Mono.just(envelope.data())
                        : Mono.empty())
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    log.debug("{}: not found in Kernel", description);
                    return Mono.empty();
                })
                .onErrorResume(Exception.class, ex -> {
                    log.warn("{}: Kernel bridge error: {}", description, ex.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Unwraps a single-object Kernel response without swallowing errors — only the envelope
     * is unwrapped ({@code success && data != null} → {@code data}, otherwise empty); any
     * HTTP/network error propagates to the caller unchanged. Use this instead of
     * {@link #unwrapObject} when the caller needs to keep its own error-handling contract
     * (e.g. a hard-blocking check that only treats 404 as "not found" and propagates
     * everything else, rather than the fail-open default most Kernel adapters use).
     */
    public static <T> Mono<T> unwrapObjectOrPropagate(WebClient.ResponseSpec responseSpec, Class<T> dataType) {
        ResolvableType envelopeType = ResolvableType.forClassWithGenerics(KernelEnvelope.class, dataType);
        ParameterizedTypeReference<KernelEnvelope<T>> typeRef = ParameterizedTypeReference.forType(envelopeType.getType());
        return responseSpec.bodyToMono(typeRef)
                .flatMap(envelope -> envelope.success() && envelope.data() != null
                        ? Mono.just(envelope.data())
                        : Mono.empty());
    }

    /**
     * Unwraps a list-valued Kernel response ({@code ApiResponseListXxx} → {@code data: Xxx[]}),
     * fail-open: 404 or any error resolves to {@link Flux#empty()}.
     */
    public static <T> Flux<T> unwrapList(WebClient.ResponseSpec responseSpec,
                                          Class<T> itemType,
                                          Logger log,
                                          String description) {
        ResolvableType listType = ResolvableType.forClassWithGenerics(List.class, itemType);
        ResolvableType envelopeType = ResolvableType.forClassWithGenerics(KernelEnvelope.class, listType);
        ParameterizedTypeReference<KernelEnvelope<List<T>>> typeRef = ParameterizedTypeReference.forType(envelopeType.getType());
        return responseSpec.bodyToMono(typeRef)
                .flatMapMany(envelope -> envelope.success() && envelope.data() != null
                        ? Flux.fromIterable(envelope.data())
                        : Flux.empty())
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    log.debug("{}: not found in Kernel", description);
                    return Flux.empty();
                })
                .onErrorResume(Exception.class, ex -> {
                    log.warn("{}: Kernel bridge error: {}", description, ex.getMessage());
                    return Flux.empty();
                });
    }
}
