package com.yowyob.tiibntick.core.trust.domain.service;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * Generates ECDSA P-256 keypairs for DID subjects that own no PKI material of
 * their own (e.g. actor profiles, FreelancerOrganizations) — DID key custody is a
 * trust-domain concern in that case, not the calling module's.
 *
 * <p>Only the public key ever leaves this class (PEM-encoded, for {@code DIDDocument});
 * the private key is discarded after generation since nothing currently signs with it.
 *
 * <p>No Spring annotations. Pure domain code.
 *
 * @author MANFOUO Braun
 */
public final class DidKeyPairGenerator {

    private DidKeyPairGenerator() {
    }

    public static String generatePublicKeyPem() {
        try {
            final KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            final KeyPair keyPair = generator.generateKeyPair();
            final String base64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("EC key generation not available", e);
        }
    }
}
