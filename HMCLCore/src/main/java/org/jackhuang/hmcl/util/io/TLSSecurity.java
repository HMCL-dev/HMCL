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

import org.jackhuang.hmcl.util.ByteArrayKey;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/// Installs TLS certificate validation that rejects known compromised CA public keys.
@NotNullByDefault
public final class TLSSecurity {
    /// Prevents instantiation.
    private TLSSecurity() {
    }

    /// Replaces the process-wide default TLS context and HTTPS socket factory.
    /// Uses the JSSE default trust store, including `javax.net.ssl.trustStore` overrides,
    /// and the `com.sun.net.ssl.checkRevocation` setting. Client credentials use the JSSE
    /// `javax.net.ssl.keyStore` properties. Normal PKIX validation and HTTPS hostname
    /// verification remain enabled. Existing connections and explicitly
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
            context.init(createKeyManagers(), factory.getTrustManagers(), null);
            SSLContext.setDefault(context);
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Failed to initialize TLS certificate protection", e);
        }
    }

    /// Loads client credentials using the JSSE key store path, type, provider, and password properties.
    /// An empty path or `NONE` loads the store without a file; PKCS11 requires `NONE`.
    /// An empty type delegates store selection to the default key manager factory.
    ///
    /// @throws GeneralSecurityException if the store or key managers cannot be initialized
    /// @throws IOException if the store cannot be read or its password is incorrect
    static KeyManager[] createKeyManagers() throws GeneralSecurityException, IOException {
        String path = System.getProperty("javax.net.ssl.keyStore", "");
        String type = System.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType());
        String provider = System.getProperty("javax.net.ssl.keyStoreProvider", "");
        String passwordProperty = System.getProperty("javax.net.ssl.keyStorePassword", "");
        boolean pkcs11 = "PKCS11".equals(type);
        if (pkcs11 && !"NONE".equals(path))
            throw new GeneralSecurityException("PKCS11 key stores require javax.net.ssl.keyStore=NONE");

        char @Nullable [] password = passwordProperty.isEmpty() ? null : passwordProperty.toCharArray();
        try {
            @Nullable KeyStore store = null;
            try (@Nullable InputStream input = path.isEmpty() || "NONE".equals(path) ? null : new FileInputStream(path)) {
                if (!type.isEmpty()) {
                    store = provider.isEmpty() ? KeyStore.getInstance(type) : KeyStore.getInstance(type, provider);
                    store.load(input, password);
                }
            }
            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            // Token providers obtain private keys without a separate key password.
            factory.init(store, pkcs11 ? null : password);
            return factory.getKeyManagers();
        } finally {
            if (password != null)
                Arrays.fill(password, '\0');
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
        BlockedKeyChecker checker = new BlockedKeyChecker();
        for (var manager : defaults.getTrustManagers()) {
            if (manager instanceof X509TrustManager x509) {
                for (X509Certificate certificate : x509.getAcceptedIssuers()) {
                    // PKIX path checkers do not inspect trust anchors.
                    if (!checker.isBlocked(certificate))
                        anchors.add(new TrustAnchor(certificate, null));
                }
            }
        }
        PKIXBuilderParameters parameters = new PKIXBuilderParameters(anchors, new X509CertSelector());
        parameters.setMaxPathLength(-1);
        parameters.setRevocationEnabled(Boolean.getBoolean("com.sun.net.ssl.checkRevocation"));
        parameters.addCertPathChecker(checker);
        return parameters;
    }

    /// Rejects compromised public keys in trust anchors and constructed certificate paths.
    /// Instances are stateless and may be used concurrently.
    @NotNullByDefault
    private static final class BlockedKeyChecker extends PKIXCertPathChecker {
        /// DER SubjectPublicKeyInfo encodings of compromised TLS and code-signing CA public keys.
        /// Cross-signed certificates are covered because they share the same public keys.
        /// Sources: [Mozilla incident announcement](https://groups.google.com/a/mozilla.org/g/dev-security-policy/c/0R3XLJL6FuE/m/Kp1v6CPuAAAJ) and
        /// [pinned certificate list](https://github.com/galadran/we-have-a-problem.com/blob/e2786ff8b707b4951f0db1f83ccaf8ac9af2f7e6/impacted-intermediates.json).
        private static final @Unmodifiable Set<ByteArrayKey> BLOCKED_KEYS = Set.of(
                // EM DV TLS CA - G3B
                ByteArrayKey.fromHexString("3076301006072A8648CE3D020106052B8104002203620004E79ED9C04A9773C4735E51111DA85AC2"
                        + "BD5E64AB374B80E12CBC2038EC1E4F0E3B812FC9859E1E67AFB04E2F8BC97F7485BA65AA911EE8A4"
                        + "E45B62D5CC03D56BE678B3C38CB4DC937CA2460E238453C892935D29D44F91E15606B63CCEDAB468"),
                // EM OV TLS CA - G3B
                ByteArrayKey.fromHexString("3076301006072A8648CE3D020106052B810400220362000473897AA0EB3CFDCA8B12402FFAF0ABC4"
                        + "E1212BAF8DEE778AC9EE2D6DB83FAC280C5E05638AE7A2ACD71C322A8E361CDEB9F69C4302B9DD8D"
                        + "73D1CC6CAAB2CC0AEE4E216CE979DD518A4AF4B626E5684CDD658037ED0C854E4CC12F4CF8646D0D"),
                // EM EV TLS CA - G3B
                ByteArrayKey.fromHexString("3076301006072A8648CE3D020106052B810400220362000425DBA85A64D397DA0B1052E9EA546E48"
                        + "7BEFEA9C9D9D68A0C5EADCFEEF75A69BB5D6FC01F42BFBCA6EB40BCD64C0767E36FFD05D97A813C5"
                        + "AA0C6C9530E79CF5F0866468F13806420AA5C851840B1E252540E790E4B654C3852ED64AF1E8657D"),
                // EM DV TLS CA - G3C
                ByteArrayKey.fromHexString("3076301006072A8648CE3D020106052B81040022036200043A7A7862C8C44D324DDD69033A6782C0"
                        + "EFEEFE4055E68A123FA0371BA3BD9194514EE172A433E3630F02BD188037D9E5149F9B77DB94A281"
                        + "DF987B2FEA888B74BDFE8FDA4C1099EE6CA19566FE3EF0E5F6B09A556CCC8BA50561DF1B2A441D01"),
                // EM OV TLS CA - G3C
                ByteArrayKey.fromHexString("3076301006072A8648CE3D020106052B8104002203620004138251CD52AC9298C1C8AAD977321DEB"
                        + "97E709BD0B4CA0ACA55DC8AD51DCFC9D1589A1597E3A5120E1EFD631C63E1835CACAE29869A62E16"
                        + "31E8A28181AB56616DC45D918ABC09F3AB0E63CF792AA4DCED7387BE37BBA569549F1C02B270ED67"),
                // EM EV TLS CA - G3C
                ByteArrayKey.fromHexString("3076301006072A8648CE3D020106052B81040022036200042D56E829A6FB82C64A4697D960AF951E"
                        + "9067AE91F926D34306DA48A8AAEB7869B6A67E6D27426E0442AA03B36E1988DFF636514EE1CF07F5"
                        + "BA4B3FF2A06645AE7D31D5866AD31AA3D246EB0B3177EE99CB7566C516CE74808A668F91F0C47FCA"),
                // EM OV CS CA - G3B
                ByteArrayKey.fromHexString("3076301006072A8648CE3D020106052B8104002203620004AABB0FB0666ACFCF01FAD52BFCFD4C2B"
                        + "F2CB3C3497DE73950E5819B61FE695098741C8C491439B34A0431CAFFE5BF9B4C1C8E8AE0001F9F4"
                        + "DC8AD82C59F3639E96CD6C3D4DB0CEFEC26C3D62517B37761372743F8E8ADD77B10064F2ABE5E050"),
                // EM OV CS CA - G3C
                ByteArrayKey.fromHexString("3076301006072A8648CE3D020106052B8104002203620004B9F0E0634702A69CA6AADD443CFD62C4"
                        + "1B9BA4B20924697D7974E7691A126FB96AFB3E34C2FD3BC5F483061F265DC2A58436347323510436"
                        + "B28F04F3862363067980E2503E02532D6546235A7165A259A1EF496F741B5FDEE7787208E4B8FAEF")
        );

        /// Returns whether the certificate contains an exact blocked SubjectPublicKeyInfo encoding.
        ///
        /// @throws CertPathValidatorException if the public key cannot be encoded
        private boolean isBlocked(Certificate certificate) throws CertPathValidatorException {
            byte @Nullable [] encoded = certificate.getPublicKey().getEncoded();
            if (encoded == null)
                throw new CertPathValidatorException("Certificate public key cannot be encoded");
            return BLOCKED_KEYS.contains(ByteArrayKey.wrap(encoded));
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
        /// @throws CertPathValidatorException if the public key is blocked or cannot be encoded
        @Override
        public void check(Certificate certificate, @Nullable Collection<String> unresolvedCritExts)
                throws CertPathValidatorException {
            if (isBlocked(certificate))
                throw new CertPathValidatorException("Certificate uses a blocked CA public key");
        }
    }
}
