package org.jackhuang.hmcl.util.logging;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class TokenFence {
    private TokenFence() {
    }

    public static String filter(String[] accessTokens, String message) {
        for (String token : accessTokens)
            message = message.replace(token, "<access token>");
        return message;
    }

    public static void filter(String[] accessTokens, Reader reader, Writer out) throws IOException {
        char[] buffer = new char[Math.max(1024, accessTokens[0].length() + 16)];
        if (accessTokens.length == 1) {
            filter(accessTokens[0], reader, out, buffer);
        } else {
            Path t1 = Files.createTempFile("hmcl-token-fence-", ".txt"), t2 = Files.createTempFile("hmcl-token-fence-", ".txt");
            try (Writer o1 = Files.newBufferedWriter(t1, StandardCharsets.UTF_8)) {
                filter(accessTokens[0], reader, o1, buffer);
            }

            for (int i = 1; i < accessTokens.length - 1; i++) {
                String token = accessTokens[i];
                if (token.length() > buffer.length) {
                    buffer = new char[token.length() + 16];
                }

                try (Reader i1 = Files.newBufferedReader(t1, StandardCharsets.UTF_8); Writer i2 = Files.newBufferedWriter(t2, StandardCharsets.UTF_8)) {
                    filter(token, i1, i2, buffer);
                }

                Path t3 = t2;
                t2 = t1;
                t1 = t3;
            }

            try (Reader r1 = Files.newBufferedReader(t1, StandardCharsets.UTF_8)) {
                filter(accessTokens[accessTokens.length - 1], r1, out, buffer);
            }

            Files.delete(t1);
            Files.delete(t2);
        }

        for (String token : accessTokens) {
            if (token.length() > buffer.length) {
                buffer = new char[token.length() + 16];
            }
            filter(token, reader, out, buffer);
        }
    }

    private static void filter(String token, Reader reader, Writer out, char[] buffer) throws IOException {
        char first = token.charAt(0);
        int start = 0, length;
        while ((length = reader.read(buffer, start, buffer.length - start)) != -1 || start != 0) {
            if (start != 0) {
                length = (length == -1 ? 0 : length) + start;
            }
            if (length < token.length()) {
                out.write(buffer, 0, length);
                return;
            }

            int tail = length - token.length() + 1, fi = findToken(buffer, tail, token);
            if (fi == -1) {
                int fi2 = indexOf(buffer, tail, length, first);
                if (fi2 == -1) {
                    out.write(buffer, 0, length);
                    start = 0;
                } else {
                    out.write(buffer, 0, fi2);
                    System.arraycopy(buffer, fi2, buffer, 0, length - fi2);
                    start = length - fi2;
                }
            } else {
                out.write(buffer, 0, fi);
                out.write("<access token>");
                start = length - fi - token.length();
                System.arraycopy(buffer, fi + token.length(), buffer, 0, start);
            }
        }
    }

    private static int findToken(char[] buffer, int tail, String token) {
        char first = token.charAt(0);
        int start = 0;
        while (true) {
            int fi = indexOf(buffer, start, tail, first);
            if (fi == -1) {
                return -1;
            }
            if (isToken(buffer, fi, token, token.length())) {
                return fi;
            }
            start = fi + 1;
        }
    }

    private static int indexOf(char[] buffer, int start, int length, char target) {
        for (int i = start; i < length; i++) {
            if (buffer[i] == target) {
                return i;
            }
        }

        return -1;
    }

    private static boolean isToken(char[] buffer, int start, String token, int length) {
        for (int i = 1; i < token.length(); i++) {
            if (buffer[start + i] != token.charAt(i)) {
                return false;
            }
        }

        return true;
    }
}
