package com.yowyob.tiibntick.core.auth.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.NoSuchElementException;

/**
 * Fetches and caches the Kernel's JWT RSA public key from its JWKS endpoint.
 *
 * <p>JWKS endpoint: https://kernel-core.yowyob.com/.well-known/jwks.json
 *
 * @author MANFOUO Braun
 */
//@Component
@Slf4j
@RequiredArgsConstructor
public class KernelPublicKeyProvider {

    private final WebClient kernelWebClient;
    private final ObjectMapper objectMapper;

    @Value("${tnt.auth.kernel.jwks-path:/.well-known/jwks.json}")
    private String jwksPath;

    @Value("${tnt.auth.kernel.jwks-kid:}")
    private String expectedKeyId;

    private PublicKey cachedPublicKey;

    @Value("${tnt.auth.kernel.jwks-required-on-startup:false}")
    private boolean jwksRequiredOnStartup;

    @PostConstruct
    public void init() {
        try {
            this.cachedPublicKey = fetchPublicKey()
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                            .maxBackoff(Duration.ofSeconds(3))
                            .doBeforeRetry(signal ->
                                log.warn("Retrying JWKS fetch (attempt {})", signal.totalRetries() + 1)
                            )
                    )
                    .block(Duration.ofSeconds(15));
        } catch (Exception ex) {
            log.warn("Could not load Kernel JWT public key from {} — JWT validation will be degraded: {}",
                    jwksPath, ex.getMessage());
        }

        if (this.cachedPublicKey == null) {
            if (jwksRequiredOnStartup) {
                throw new IllegalStateException(
                    "CRITICAL: Could not load Kernel JWT public key from " + jwksPath +
                    " (tnt.auth.kernel.jwks-required-on-startup=true)"
                );
            }
            log.warn("Kernel JWT public key NOT loaded — JWT signature validation disabled. " +
                     "Set tnt.auth.kernel.jwks-required-on-startup=true to fail-fast.");
            return;
        }

        log.info("Kernel JWT public key loaded successfully");
    }

    public PublicKey getPublicKey() {
        return cachedPublicKey;
    }

    private Mono<PublicKey> fetchPublicKey() {
        return kernelWebClient
                .get()
                .uri(jwksPath)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::extractRsaPublicKey)
                .doOnSuccess(key -> log.debug("Successfully parsed RSA public key from JWKS"))
                .doOnError(e -> {
                    String msg = e instanceof WebClientResponseException wcre
                            ? "HTTP " + wcre.getStatusCode()
                            : e.getMessage();
                    log.error("Failed to fetch JWKS from Kernel: {}", msg);
                });
    }

    private PublicKey extractRsaPublicKey(String jwksJson) {
        try {
            JsonNode jwks = objectMapper.readTree(jwksJson);
            JsonNode keys = jwks.get("keys");

            if (keys == null || !keys.isArray() || keys.isEmpty()) {
                throw new IllegalStateException("JWKS response contains no keys");
            }

            JsonNode rsaKey = null;
            for (JsonNode key : keys) {
                String kty = key.get("kty").asText("");
                String kid = key.get("kid").asText("");

                if (!"RSA".equals(kty)) continue;

                if (expectedKeyId != null && !expectedKeyId.isBlank()) {
                    if (expectedKeyId.equals(kid)) {
                        rsaKey = key;
                        break;
                    }
                } else {
                    rsaKey = key;
                    break;
                }
            }

            if (rsaKey == null) {
                throw new NoSuchElementException("No RSA key found in JWKS");
            }

            String n = rsaKey.get("n").asText();
            String e = rsaKey.get("e").asText();

            return buildRsaPublicKey(n, e);

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse JWKS: " + ex.getMessage(), ex);
        }
    }

    private PublicKey buildRsaPublicKey(String base64Modulus, String base64Exponent) throws Exception {
        byte[] modulusBytes = Base64.getUrlDecoder().decode(base64Modulus);
        byte[] exponentBytes = Base64.getUrlDecoder().decode(base64Exponent);

        RSAPublicKeySpec spec = new RSAPublicKeySpec(
                new java.math.BigInteger(1, modulusBytes),
                new java.math.BigInteger(1, exponentBytes)
        );

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }
}