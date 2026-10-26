// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.gradle.utils;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies official-build classification using disposable RSA keys.
@NotNullByDefault
final class OfficialBuildVerifierTest {
    /// Accepts only the pinned public key with its matching private key.
    @Test
    void checksFingerprintAndKeyPair(@TempDir Path directory) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var official = generator.generateKeyPair();
        var replacement = generator.generateKeyPair();
        Path publicKey = directory.resolve("public.der");
        Path privateKey = directory.resolve("private.der");
        Files.write(publicKey, official.getPublic().getEncoded());
        Files.write(privateKey, official.getPrivate().getEncoded());
        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(official.getPublic().getEncoded()));

        assertTrue(OfficialBuildVerifier.isOfficialBuild(publicKey, privateKey.toString(), fingerprint));
        Files.write(privateKey, replacement.getPrivate().getEncoded());
        assertFalse(OfficialBuildVerifier.isOfficialBuild(publicKey, privateKey.toString(), fingerprint));
        Files.write(publicKey, replacement.getPublic().getEncoded());
        assertFalse(OfficialBuildVerifier.isOfficialBuild(publicKey, privateKey.toString(), fingerprint));
        Files.write(privateKey, official.getPrivate().getEncoded());
        assertFalse(OfficialBuildVerifier.isOfficialBuild(publicKey, privateKey.toString(), fingerprint));
    }

    /// Treats absent and invalid key files as unofficial without throwing.
    @Test
    void rejectsMissingAndMalformedKeys(@TempDir Path directory) throws Exception {
        Path publicKey = directory.resolve("public.der");
        Path privateKey = directory.resolve("private.der");
        String fingerprint = "0".repeat(64);
        assertFalse(OfficialBuildVerifier.isOfficialBuild(publicKey, null, fingerprint));
        assertFalse(OfficialBuildVerifier.isOfficialBuild(publicKey, "", fingerprint));
        assertFalse(OfficialBuildVerifier.isOfficialBuild(publicKey, privateKey.toString(), fingerprint));

        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        Files.write(publicKey, generator.generateKeyPair().getPublic().getEncoded());
        fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(publicKey)));
        assertFalse(OfficialBuildVerifier.isOfficialBuild(publicKey, privateKey.toString(), fingerprint));
        Files.writeString(privateKey, "invalid private key");
        assertFalse(OfficialBuildVerifier.isOfficialBuild(publicKey, privateKey.toString(), fingerprint));
        Files.writeString(publicKey, "invalid public key");
        fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(publicKey)));
        assertFalse(OfficialBuildVerifier.isOfficialBuild(publicKey, privateKey.toString(), fingerprint));
    }
}
