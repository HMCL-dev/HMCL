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
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.CertificateFactory;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.List;
import java.util.Objects;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises the incident fixtures with offline PKIX validation.
///
/// The compromised certificates also match the original Mozilla report attachments:
/// [original issuances](https://bugzilla.mozilla.org/attachment.cgi?id=9664066) and
/// [cross-signs](https://bugzilla.mozilla.org/attachment.cgi?id=9664067).
///
/// Public certificate fixtures come from the following pinned source files.
/// SHA-256 digests refer to the original downloaded bytes.
///
/// | Source | Local fixtures | SHA-256 |
/// | --- | --- | --- |
/// | [impacted-intermediates.json](https://github.com/galadran/we-have-a-problem.com/blob/e2786ff8b707b4951f0db1f83ccaf8ac9af2f7e6/impacted-intermediates.json) | `compromised.pem` | `f67b3abe9b4879f4e91874ed041beccd71257f1db41c588c724ccf12aa90c59e` |
/// | [roots.json](https://github.com/galadran/we-have-a-problem.com/blob/e2786ff8b707b4951f0db1f83ccaf8ac9af2f7e6/roots.json) | `roots.pem` | `119f32cd81a6b5a4e3c4c2df5fe07daa90dc43cafa123a62f0394f1895d92380` |
/// | [chains.zip](https://github.com/galadran/we-have-a-problem.com/blob/e2786ff8b707b4951f0db1f83ccaf8ac9af2f7e6/chains.zip) | The 12 TLS chain PEM files | `8a2edc81b6c51e3ad27d45ca1e441ec84c961e2dee0fc547f2a02c0f74315f95` |
@NotNullByDefault
class TLSSecurityTest {
    /// Reads an independent array of public certificates from a bundled PEM fixture.
    private static X509Certificate[] readCertificates(String name) throws Exception {
        try (InputStream input = Objects.requireNonNull(TLSSecurityTest.class.getResourceAsStream("tls/" + name))) {
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

    /// Returns an initialized checker from the application's PKIX configuration.
    private static PKIXCertPathChecker newChecker() throws Exception {
        PKIXCertPathChecker checker = TLSSecurity.createParameters(trustStore(readCertificates("roots.pem")))
                .getCertPathCheckers().get(0);
        checker.init(false);
        return checker;
    }

    /// Covers all eight keys and their cross-signed certificates, including code-signing CAs.
    @Test
    void blocksAllPublishedCertificates() throws Exception {
        X509Certificate[] certificates = readCertificates("compromised.pem");
        assertEquals(16, certificates.length);
        PKIXCertPathChecker checker = newChecker();
        Set<String> fingerprints = new HashSet<>();
        for (X509Certificate certificate : certificates) {
            fingerprints.add(HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(certificate.getPublicKey().getEncoded())));
            assertThrows(CertPathValidatorException.class, () -> checker.check(certificate),
                    certificate.getSubjectX500Principal().toString());
        }
        assertEquals(Set.of(
                "EBED6475C2C2D80FCB199F3FE8DD6E6B3CE60B77EB15D4E42C3DEC0C6F5B5105",
                "43BFFA19D6EF651451F6841E019931E96F6F2EAEFF28AB4B6F1CE4576DE2717B",
                "E0254C7FCDC265E2F0F8A428DABB75AB4AE039B4872D62F5EE57F0877D5A8A41",
                "49DD353DC2B919A3770645117D0E60C16115E7D1BC259EEFE734B00EA434FE5F",
                "63ECEFEE431D49523BC1136C4E72D0206A477086CAF606CD5CF025686C109CF8",
                "5C0660A2B164910D8FF589C18E14552E9E0EC237DCEFE2CB736D41A4811F81A0",
                "EC4FFA1AD0AC88081314B7AAFFEF6A91F97CD57291BB5BA686C5415F5EB4F6B6",
                "92C063D67E8F14DB71D288E11266FFDAF69CBF115F7BAB14F3BC2F68D25E0E30"
        ), fingerprints);
    }

    /// Matches every certificate to the full-certificate fingerprints in Mozilla's disclosure.
    /// Source: [Mozilla disclosure](https://bugzilla.mozilla.org/attachment.cgi?id=9664786).
    @Test
    void matchesMozillaDisclosure() throws Exception {
        Set<String> fingerprints = new HashSet<>();
        for (X509Certificate certificate : readCertificates("compromised.pem")) {
            fingerprints.add(HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded())));
        }
        assertEquals(Set.of(
                "E7568FE6E12331F4FD7DE61DA4C7559D0F8EB192A218D4741A3BA75184333838",
                "98B896625C0C230ADEEC3EB2CF6AAD5001ECF9156A68644E82374CE5833BC25B",
                "42C4ACCD12C4F59597925E1A08A7A90102608543809308D50EC427E118BA1493",
                "432EC25F0D28DE9006D97BF752F17BBB2CD41F669933FCB072D9A1148D0261F3",
                "CB6E15165AC802E9B3F993F16C25D41F4E81C21EBDE71AE1B012AC00BF9C7C3F",
                "0164D7CEAE5BB909C272AF151FF44B1177AE9B47D9424B94F1D9558C30934D19",
                "9E39DE2881273ACC225A149C72BA3E0F51424963669AB633EE1DC7DF5DB067B4",
                "4B2460A466F95FD40113BCDCCA2CEB29B3B093921DE45474DA7DF48874C630B1",
                "579480AEA166290BD3C643589E795FC0D0E54E86505D180F1DFF99EFD3199512",
                "5F1DED4DD590AF74631290CF073B122A2AAC6A1FA479778A0BD43579BD832699",
                "45A883F349AEDFD5F3A649B5498FFB121712BA8B448F3F2A91DBC5A766FCDDBE",
                "FD9D5C2DBBD4995AB69B8D50C439D9527B00529199C2FD01B2174EB58F07021F",
                "7485B51C0ED192878FA1D2F948FB529AE1842CC5086C4B67F7BE5B2039DE9489",
                "62B7F9868263F72F6911FC78CEAF72C1979158B7368BF648683BB2AA39B02E72",
                "A496E23AC0CAB8155FC9B117B97EAE368EFCBB9041AEC8804D5A308D2D01C2D8",
                "84112972B8E59D14560AE7E7119654474AAD119990CF3722BFC1E3B91615E1FD"
        ), fingerprints);
    }

    /// Proves otherwise-valid TLS paths fail because of the blocklist, for both signing roots.
    @Test
    void rejectsAllPublishedTlsChains() throws Exception {
        for (String type : List.of("dv", "ov", "ev")) {
            for (String generation : List.of("g3b", "g3c")) {
                for (String crossSign : List.of("", "-aaa")) {
                    String name = type + "-" + generation + crossSign + "-emudhra.we-have-a-problem.com.pem";
                    X509Certificate[] chain = readCertificates(name);
                    PKIXBuilderParameters parameters = TLSSecurity.createParameters(trustStore(readCertificates("roots.pem")));
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
        PKIXBuilderParameters parameters = TLSSecurity.createParameters(store);
        assertEquals(1, parameters.getTrustAnchors().size());
        assertEquals(root, parameters.getTrustAnchors().iterator().next().getTrustedCert());
        assertDoesNotThrow(() -> trustManager(parameters).checkServerTrusted(new X509Certificate[]{root}, "RSA"));
        assertThrows(InvalidAlgorithmParameterException.class, () -> TLSSecurity.createParameters(trustStore(compromised)));
    }

    /// Checks intermediates supplied by the path builder rather than by the peer.
    @Test
    void rejectsIntermediateFromCertStore() throws Exception {
        X509Certificate[] chain = readCertificates("dv-g3b-aaa-emudhra.we-have-a-problem.com.pem");
        PKIXBuilderParameters parameters = TLSSecurity.createParameters(trustStore(readCertificates("roots.pem")));
        parameters.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(List.of(chain))));
        X509Certificate[] leaf = {chain[0]};
        assertThrows(CertificateException.class, () -> trustManager(parameters).checkServerTrusted(leaf, "ECDHE_ECDSA"));
        parameters.setCertPathCheckers(List.of());
        assertDoesNotThrow(() -> trustManager(parameters).checkServerTrusted(leaf, "ECDHE_ECDSA"));
    }

    /// Exercises concurrent checks on a shared instance and its shallow clones.
    @Test
    void supportsConcurrentChecksAndClones() throws Exception {
        X509Certificate[] blocked = readCertificates("compromised.pem");
        X509Certificate[] roots = readCertificates("roots.pem");
        PKIXCertPathChecker original = newChecker();
        original.check(roots[0]);
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                PKIXCertPathChecker checker = i % 2 == 0 ? original : (PKIXCertPathChecker) original.clone();
                tasks.add(executor.submit(() -> {
                    for (int iteration = 0; iteration < 100; iteration++) {
                        X509Certificate root = roots[iteration % roots.length];
                        for (X509Certificate certificate : blocked) {
                            assertThrows(CertPathValidatorException.class, () -> checker.check(certificate));
                            assertDoesNotThrow(() -> checker.check(root));
                        }
                        assertDoesNotThrow(() -> checker.init(true));
                    }
                }));
            }
            for (Future<?> task : tasks)
                task.get();
            assertDoesNotThrow(() -> original.check(roots[0]));
            assertThrows(CertPathValidatorException.class, () -> original.check(blocked[0]));
        } finally {
            executor.shutdownNow();
        }
    }

    /// Installs both process defaults and restores them after the assertion.
    @Test
    void installsProcessDefaults() throws Exception {
        SSLContext previousContext = SSLContext.getDefault();
        SSLSocketFactory previousFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
        try {
            TLSSecurity.install();
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
        assertDoesNotThrow(() -> newChecker().check(chain[0]));
        X509ExtendedTrustManager manager = trustManager(TLSSecurity.createParameters(trustStore(readCertificates("roots.pem"))));
        assertThrows(CertificateException.class, () -> manager.checkServerTrusted(new X509Certificate[]{chain[0]}, "ECDHE_ECDSA"));
    }
}
