/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.gradle.utils;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

/// Checks whether a build has the pinned public key and its matching signing key.
@NotNullByDefault
public final class OfficialBuildVerifier {
    /// Prevents instantiation.
    private OfficialBuildVerifier() {
    }

    /// Verifies the public-key fingerprint and signs a random challenge to check the private key.
    ///
    /// Reads key files without modifying them or reporting their contents. Missing, unreadable,
    /// malformed, or mismatched keys return false. Signing uses the same algorithm as the launcher.
    ///
    /// @param publicKeyFile the X.509-encoded RSA public-key file bundled with the launcher
    /// @param privateKeyLocation the PKCS#8-encoded RSA private-key path, or null if unconfigured
    /// @param expectedSha256 the pinned lowercase SHA-256 fingerprint of the public-key file
    /// @return true only if the pinned public key verifies a signature made by the supplied private key
    public static boolean isOfficialBuild(Path publicKeyFile, @Nullable String privateKeyLocation,
                                          String expectedSha256) {
        if (privateKeyLocation == null || privateKeyLocation.isBlank()) {
            return false;
        }
        try {
            byte[] publicKeyBytes = Files.readAllBytes(publicKeyFile);
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(publicKeyBytes));
            if (!expectedSha256.equals(fingerprint)) {
                return false;
            }

            KeyFactory factory = KeyFactory.getInstance("RSA");
            var publicKey = factory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            var privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(
                    Files.readAllBytes(Path.of(privateKeyLocation))));
            byte[] challenge = new byte[32];
            new SecureRandom().nextBytes(challenge);

            Signature signer = Signature.getInstance("SHA512withRSA");
            signer.initSign(privateKey);
            signer.update(challenge);
            byte[] signature = signer.sign();

            Signature verifier = Signature.getInstance("SHA512withRSA");
            verifier.initVerify(publicKey);
            verifier.update(challenge);
            return verifier.verify(signature);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | SecurityException e) {
            return false;
        }
    }
}
