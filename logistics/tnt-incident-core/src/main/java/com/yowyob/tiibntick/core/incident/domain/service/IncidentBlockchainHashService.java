package com.yowyob.tiibntick.core.incident.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Pure domain service providing SHA-256 block hashing and chain integrity verification.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public class IncidentBlockchainHashService {

    /**
     * Computes the SHA-256 hash for a new blockchain block.
     *
     * @param blockIndex    position of the block in the chain
     * @param previousHash  hash of the preceding block
     * @param eventType     type of the event being recorded
     * @param payload       JSON payload of the event
     * @param timestamp     ISO-8601 timestamp string
     * @param nonce         anti-collision nonce
     * @return hex-encoded SHA-256 hash string
     */
    public String computeHash(long blockIndex, String previousHash,
                               String eventType, String payload,
                               String timestamp, long nonce) {
        String data = blockIndex + previousHash + eventType + payload + timestamp + nonce;
        return sha256(data);
    }

    /**
     * Derives a unique chain identifier for an incident blockchain chain.
     *
     * @param incidentId the incident UUID
     * @param tenantId   the tenant UUID
     * @return a unique chain ID prefixed with {@code INC-}
     */
    public String computeChainId(String incidentId, String tenantId) {
        return "INC-" + sha256(incidentId + tenantId).substring(0, 16).toUpperCase();
    }

    /**
     * Verifies that a block hash matches the expected computation.
     *
     * @return {@code true} if the hash is valid
     */
    public boolean verify(String expectedHash, long blockIndex, String previousHash,
                           String eventType, String payload, String timestamp, long nonce) {
        String computed = computeHash(blockIndex, previousHash, eventType, payload, timestamp, nonce);
        return computed.equals(expectedHash);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
