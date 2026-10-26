// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * @author huangyuhui
 */
public final class DigestUtils {

    private DigestUtils() {
    }

    private static final int STREAM_BUFFER_LENGTH = 1024;

    public static boolean isSha1Digest(String digest) {
        if (digest == null || digest.length() != 40) return false;

        for (int i = 0; i < digest.length(); i++) {
            char ch = digest.charAt(i);
            if ((ch < '0' || ch > '9') && (ch < 'a' || ch > 'f') && (ch < 'A' || ch > 'F')) {
                return false;
            }
        }

        return true;
    }

    /// Returns whether `digest` is a 512-bit hexadecimal SHA-512 digest (128 hex digits).
    ///
    /// @param digest the digest string to check, or `null`
    /// @return `true` when `digest` is a valid SHA-512 hex digest
    public static boolean isSha512Digest(String digest) {
        if (digest == null || digest.length() != 128) return false;

        for (int i = 0; i < digest.length(); i++) {
            char ch = digest.charAt(i);
            if ((ch < '0' || ch > '9') && (ch < 'a' || ch > 'f') && (ch < 'A' || ch > 'F')) {
                return false;
            }
        }

        return true;
    }

    public static MessageDigest getDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static byte[] digest(String algorithm, byte[] data) {
        return getDigest(algorithm).digest(data);
    }

    public static byte[] digest(String algorithm, Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return digest(algorithm, is);
        }
    }

    public static byte[] digest(String algorithm, InputStream data) throws IOException {
        return digest(getDigest(algorithm), data);
    }

    public static byte[] digest(MessageDigest digest, InputStream data) throws IOException {
        return updateDigest(digest, data).digest();
    }

    public static String digestToString(String algorithm, byte[] data) {
        return HexFormat.of().formatHex(digest(algorithm, data));
    }

    public static String digestToString(String algorithm, Path path) throws IOException {
        return HexFormat.of().formatHex(digest(algorithm, path));
    }

    public static String digestToString(String algorithm, InputStream data) throws IOException {
        return HexFormat.of().formatHex(digest(algorithm, data));
    }

    private static final ThreadLocal<byte[]> threadLocalBuffer = ThreadLocal.withInitial(() -> new byte[STREAM_BUFFER_LENGTH]);

    public static MessageDigest updateDigest(MessageDigest digest, InputStream data) throws IOException {
        byte[] buffer = threadLocalBuffer.get();
        int read = data.read(buffer, 0, STREAM_BUFFER_LENGTH);

        while (read > -1) {
            digest.update(buffer, 0, read);
            read = data.read(buffer, 0, STREAM_BUFFER_LENGTH);
        }

        return digest;
    }

}
