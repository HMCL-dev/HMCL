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
import org.junit.jupiter.api.Test;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises the incident fixtures with offline PKIX validation.
@NotNullByDefault
class TlsSecurityTest {
    /// Reads an independent array of public certificates from a bundled PEM fixture.
    private static X509Certificate[] readCertificates(String name) throws Exception {
        try (InputStream input = Objects.requireNonNull(TlsSecurityTest.class.getResourceAsStream("tls/" + name))) {
            return CertificateFactory.getInstance("X.509").generateCertificates(input).toArray(X509Certificate[]::new);
        }
    }

    /// Creates a trust store containing exactly the supplied certificates.
    private static KeyStore trustStore(X509Certificate... certificates) throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        for (int i = 0; i < certificates.length; i++)
            store.setCertificateEntry("certificate-" + i, certificates[i]);
        return store;
    }

    /// Creates an extended trust manager at a fixed date within the fixtures' validity periods.
    private static X509ExtendedTrustManager trustManager(PKIXBuilderParameters parameters) throws Exception {
        parameters.setDate(Date.from(Instant.parse("2026-10-22T00:00:00Z")));
        parameters.setRevocationEnabled(false);
        TrustManagerFactory factory = TrustManagerFactory.getInstance("PKIX");
        factory.init(new CertPathTrustManagerParameters(parameters));
        return (X509ExtendedTrustManager) factory.getTrustManagers()[0];
    }

    /// Covers all eight keys and their cross-signed certificates, including code-signing CAs.
    @Test
    void blocksAllPublishedCertificates() throws Exception {
        X509Certificate[] certificates = readCertificates("compromised.pem");
        assertEquals(16, certificates.length);
        for (X509Certificate certificate : certificates)
            assertTrue(TlsSecurity.isBlocked(certificate), certificate.getSubjectX500Principal().toString());
    }

    /// Proves otherwise-valid TLS paths fail because of the blocklist, for both signing roots.
    @Test
    void rejectsAllPublishedTlsChains() throws Exception {
        for (String type : List.of("dv", "ov", "ev")) {
            for (String generation : List.of("g3b", "g3c")) {
                for (String crossSign : List.of("", "-aaa")) {
                    String name = type + "-" + generation + crossSign + "-emudhra.we-have-a-problem.com.pem";
                    X509Certificate[] chain = readCertificates(name);
                    PKIXBuilderParameters parameters = TlsSecurity.createParameters(trustStore(readCertificates("roots.pem")));
                    X509ExtendedTrustManager protectedManager = trustManager(parameters);
                    assertThrows(CertificateException.class, () -> protectedManager.checkServerTrusted(chain, "ECDHE_ECDSA"), name);
                    assertThrows(CertificateException.class, () -> protectedManager.checkServerTrusted(chain, "ECDHE_ECDSA", (Socket) null), name);
                    assertThrows(CertificateException.class, () -> protectedManager.checkServerTrusted(chain, "ECDHE_ECDSA", (SSLEngine) null), name);
                    assertThrows(CertificateException.class, () -> protectedManager.checkClientTrusted(chain, "EC"), name);

                    // Removing only the blocklist must make the same server path valid.
                    parameters.setCertPathCheckers(List.of());
                    assertDoesNotThrow(() -> trustManager(parameters).checkServerTrusted(chain, "ECDHE_ECDSA"), name);
                }
            }
        }
    }

    /// Removes compromised certificates even when explicitly installed as trust anchors.
    @Test
    void filtersCompromisedTrustAnchors() throws Exception {
        X509Certificate[] compromised = readCertificates("compromised.pem");
        X509Certificate root = readCertificates("roots.pem")[2];
        KeyStore store = trustStore(compromised);
        store.setCertificateEntry("unaffected-root", root);
        PKIXBuilderParameters parameters = TlsSecurity.createParameters(store);
        assertEquals(1, parameters.getTrustAnchors().size());
        assertEquals(root, parameters.getTrustAnchors().iterator().next().getTrustedCert());
        assertDoesNotThrow(() -> trustManager(parameters).checkServerTrusted(new X509Certificate[]{root}, "RSA"));
        assertThrows(InvalidAlgorithmParameterException.class, () -> TlsSecurity.createParameters(trustStore(compromised)));
    }

    /// Checks intermediates supplied by the path builder rather than by the peer.
    @Test
    void rejectsIntermediateFromCertStore() throws Exception {
        X509Certificate[] chain = readCertificates("dv-g3b-aaa-emudhra.we-have-a-problem.com.pem");
        PKIXBuilderParameters parameters = TlsSecurity.createParameters(trustStore(readCertificates("roots.pem")));
        parameters.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(List.of(chain))));
        X509Certificate[] leaf = {chain[0]};
        assertThrows(CertificateException.class, () -> trustManager(parameters).checkServerTrusted(leaf, "ECDHE_ECDSA"));
        parameters.setCertPathCheckers(List.of());
        assertDoesNotThrow(() -> trustManager(parameters).checkServerTrusted(leaf, "ECDHE_ECDSA"));
    }

    /// Installs both process defaults and restores them after the assertion.
    @Test
    void installsProcessDefaults() throws Exception {
        SSLContext previousContext = SSLContext.getDefault();
        SSLSocketFactory previousFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
        try {
            TlsSecurity.install();
            assertNotSame(previousContext, SSLContext.getDefault());
            assertNotSame(previousFactory, HttpsURLConnection.getDefaultSSLSocketFactory());
        } finally {
            SSLContext.setDefault(previousContext);
            HttpsURLConnection.setDefaultSSLSocketFactory(previousFactory);
        }
    }

    /// Keeps ordinary validation active for a leaf that does not contain a blocked key.
    @Test
    void rejectsUntrustedLeafWithoutIntermediate() throws Exception {
        X509Certificate[] chain = readCertificates("dv-g3b-aaa-emudhra.we-have-a-problem.com.pem");
        assertFalse(TlsSecurity.isBlocked(chain[0]));
        X509ExtendedTrustManager manager = trustManager(TlsSecurity.createParameters(trustStore(readCertificates("roots.pem"))));
        assertThrows(CertificateException.class, () -> manager.checkServerTrusted(new X509Certificate[]{chain[0]}, "ECDHE_ECDSA"));
    }
}
