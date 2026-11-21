// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.gradle.utils;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/// Creates checksums and HMCL signatures for release artifacts.
@NotNullByDefault
public final class ArtifactUtils {
    /// Maps checksum algorithms to file extensions, in checksum creation order.
    public static final @Unmodifiable List<Map.Entry<String, String>> ALGORITHMS = List.of(
            Map.entry("SHA-1", "sha1"),
            Map.entry("SHA-256", "sha256"),
            Map.entry("SHA-512", "sha512")
    );

    /// Prevents instantiation of this utility class.
    private ArtifactUtils() {
    }

    /// Returns a newly allocated digest of all the supplied bytes without modifying them.
    ///
    /// @param algorithm the message-digest algorithm name
    /// @param bytes the input bytes
    /// @return the digest bytes
    /// @throws NoSuchAlgorithmException if no provider supports the algorithm
    public static byte[] digest(String algorithm, byte[] bytes) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance(algorithm).digest(bytes);
    }

    /// Writes SHA-1, SHA-256, and SHA-512 checksums beside the supplied file.
    /// Each output name appends the corresponding extension from [#ALGORITHMS] to
    /// the full file name. Existing outputs are overwritten with lowercase hexadecimal
    /// text followed by a single LF, encoded as UTF-8. The input is read separately
    /// for each checksum; outputs already written are retained if a later operation fails.
    ///
    /// @param file the artifact to read
    /// @throws IOException if reading the artifact or writing a checksum fails
    /// @throws NoSuchAlgorithmException if a required digest algorithm is unavailable
    public static void createChecksum(File file) throws IOException, NoSuchAlgorithmException {
        for (var entry : ALGORITHMS) {
            var checksumFile = new File(file.getParentFile(), file.getName() + '.' + entry.getValue());
            Files.writeString(checksumFile.toPath(),
                    HexFormat.of().formatHex(digest(entry.getKey(), Files.readAllBytes(file.toPath()))) + '\n');
        }
    }

    /// Writes an HMCL signature into a JAR, replacing any existing signature.
    /// The SHA512withRSA signature covers the SHA-512 digests of each entry's UTF-8
    /// name and uncompressed contents, in entry-name order, including directory entries
    /// and excluding `META-INF/hmcl_signature`. All opened resources are closed before
    /// this method returns. The JAR must contain the `META-INF` directory and must not
    /// be modified concurrently. A failure while writing does not guarantee rollback.
    ///
    /// @param jar the JAR to read and modify
    /// @param keyLocation the path to a DER-encoded PKCS #8 RSA private key
    /// @throws IOException if reading the key or JAR, or writing the signature, fails
    /// @throws GeneralSecurityException if a required algorithm is unavailable,
    ///         the key is invalid, or signing fails
    public static void attachSignature(File jar, String keyLocation) throws IOException, GeneralSecurityException {
        var privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Files.readAllBytes(new File(keyLocation).toPath())));
        var signer = Signature.getInstance("SHA512withRSA");
        signer.initSign(privateKey);
        try (var zip = new ZipFile(jar)) {
            var entries = zip.stream()
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .filter(entry -> !entry.getName().equals("META-INF/hmcl_signature"))
                    .iterator();
            while (entries.hasNext()) {
                var entry = entries.next();
                signer.update(digest("SHA-512", entry.getName().getBytes(StandardCharsets.UTF_8)));
                try (var input = zip.getInputStream(entry)) {
                    signer.update(digest("SHA-512", input.readAllBytes()));
                }
            }
        }
        byte[] signature = signer.sign();
        try (var zipfs = FileSystems.newFileSystem(URI.create("jar:" + jar.toURI()), Map.of())) {
            Files.write(zipfs.getPath("META-INF/hmcl_signature"), signature);
        }
    }

    /// Returns a sibling path with the file name's last extension replaced.
    /// If the name has no dot, the extension is appended. A leading dot also counts
    /// as an extension separator. This method does not access the file system.
    ///
    /// @param jarFile the original artifact path
    /// @param ext the replacement extension, without its leading dot; may be empty
    /// @return the sibling artifact path
    public static File resolveArtifactFile(File jarFile, String ext) {
        String name = jarFile.getName();
        int dot = name.lastIndexOf('.');
        return new File(jarFile.getParentFile(), (dot < 0 ? name : name.substring(0, dot)) + '.' + ext);
    }
}
