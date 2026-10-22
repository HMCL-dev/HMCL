/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.io;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

/// Installs TLS certificate validation that rejects known compromised CA public keys.
@NotNullByDefault
public final class TlsSecurity {
    /// SHA-256 hashes of DER SubjectPublicKeyInfo, including both TLS and code-signing CAs.
    /// Cross-signed certificates are covered because they share the same public keys.
    /// Sources: [Mozilla incident announcement](https://groups.google.com/a/mozilla.org/g/dev-security-policy/c/0R3XLJL6FuE/m/Kp1v6CPuAAAJ) and
    /// [pinned certificate list](https://github.com/galadran/we-have-a-problem.com/blob/e2786ff8b707b4951f0db1f83ccaf8ac9af2f7e6/impacted-intermediates.json).
    private static final @Unmodifiable Set<String> BLOCKED_KEYS = Set.of(
            // EM DV TLS CA - G3B
            "EBED6475C2C2D80FCB199F3FE8DD6E6B3CE60B77EB15D4E42C3DEC0C6F5B5105",
            // EM OV TLS CA - G3B
            "43BFFA19D6EF651451F6841E019931E96F6F2EAEFF28AB4B6F1CE4576DE2717B",
            // EM EV TLS CA - G3B
            "E0254C7FCDC265E2F0F8A428DABB75AB4AE039B4872D62F5EE57F0877D5A8A41",
            // EM DV TLS CA - G3C
            "49DD353DC2B919A3770645117D0E60C16115E7D1BC259EEFE734B00EA434FE5F",
            // EM OV TLS CA - G3C
            "63ECEFEE431D49523BC1136C4E72D0206A477086CAF606CD5CF025686C109CF8",
            // EM EV TLS CA - G3C
            "5C0660A2B164910D8FF589C18E14552E9E0EC237DCEFE2CB736D41A4811F81A0",
            // EM OV CS CA - G3B
            "EC4FFA1AD0AC88081314B7AAFFEF6A91F97CD57291BB5BA686C5415F5EB4F6B6",
            // EM OV CS CA - G3C
            "92C063D67E8F14DB71D288E11266FFDAF69CBF115F7BAB14F3BC2F68D25E0E30"
    );

    /// Prevents instantiation.
    private TlsSecurity() {
    }

    /// Replaces the process-wide default TLS context and HTTPS socket factory.
    /// Uses the JSSE default trust store, including `javax.net.ssl.trustStore` overrides,
    /// and the `com.sun.net.ssl.checkRevocation` setting. Normal PKIX validation and
    /// HTTPS hostname verification remain enabled. Existing connections and explicitly
    /// configured TLS contexts are not changed.
    ///
    /// This method must be called before starting network activity. Initialization failure
    /// throws an exception so that startup cannot continue without the blocklist.
    ///
    /// @throws IllegalStateException if the protected TLS context cannot be initialized
    public static void install() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            TrustManagerFactory factory = TrustManagerFactory.getInstance("PKIX");
            factory.init(new CertPathTrustManagerParameters(createParameters(null)));
            context.init(null, factory.getTrustManagers(), null);
            SSLContext.setDefault(context);
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to initialize TLS certificate protection", e);
        }
    }

    /// Creates PKIX parameters with compromised trust anchors removed and a path checker.
    /// A null store selects the JSSE default store. Revocation follows the JSSE system property.
    ///
    /// @throws GeneralSecurityException if initialization fails or no usable trust anchors remain
    static PKIXBuilderParameters createParameters(@Nullable KeyStore store) throws GeneralSecurityException {
        TrustManagerFactory defaults = TrustManagerFactory.getInstance("PKIX");
        defaults.init(store);
        Set<TrustAnchor> anchors = new HashSet<>();
        for (var manager : defaults.getTrustManagers()) {
            if (manager instanceof X509TrustManager x509) {
                for (X509Certificate certificate : x509.getAcceptedIssuers()) {
                    // PKIX path checkers do not inspect trust anchors.
                    if (!isBlocked(certificate))
                        anchors.add(new TrustAnchor(certificate, null));
                }
            }
        }
        PKIXBuilderParameters parameters = new PKIXBuilderParameters(anchors, new X509CertSelector());
        parameters.setMaxPathLength(-1);
        parameters.setRevocationEnabled(Boolean.getBoolean("com.sun.net.ssl.checkRevocation"));
        parameters.addCertPathChecker(new BlockedKeyChecker());
        return parameters;
    }

    /// Returns whether the certificate contains a blocked SubjectPublicKeyInfo.
    ///
    /// @throws GeneralSecurityException if SHA-256 is unavailable
    static boolean isBlocked(Certificate certificate) throws GeneralSecurityException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getPublicKey().getEncoded());
        return BLOCKED_KEYS.contains(HexFormat.of().withUpperCase().formatHex(digest));
    }

    /// Rejects compromised keys in the constructed path, including fetched intermediates.
    @NotNullByDefault
    private static final class BlockedKeyChecker extends PKIXCertPathChecker {
        /// Creates a stateless checker supporting either validation direction.
        private BlockedKeyChecker() {
        }

        @Override
        public void init(boolean forward) {
        }

        @Override
        public boolean isForwardCheckingSupported() {
            return true;
        }

        @Override
        public @Unmodifiable Set<String> getSupportedExtensions() {
            return Set.of();
        }

        /// Rejects a blocked public key without consuming any critical extensions.
        ///
        /// @throws CertPathValidatorException if the key is blocked or cannot be checked
        @Override
        public void check(Certificate certificate, @Nullable Collection<String> unresolvedCritExts)
                throws CertPathValidatorException {
            try {
                if (isBlocked(certificate))
                    throw new CertPathValidatorException("Certificate uses a blocked CA public key");
            } catch (GeneralSecurityException e) {
                throw new CertPathValidatorException("Certificate public key rejected", e);
            }
        }
    }
}
