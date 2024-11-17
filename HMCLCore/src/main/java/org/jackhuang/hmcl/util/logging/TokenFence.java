package org.jackhuang.hmcl.util.logging;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

final class TokenFence {
    private TokenFence() {
    }

    public static String filter(String[] accessTokens, String message) {
        for (String token : accessTokens)
            message = message.replace(token, "<access token>");
        return message;
    }

    public static void filter(String[] accessTokens, Reader reader, Writer out) throws IOException {
        char[] buffer = new char[1024];
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
        while ((length = reader.read(buffer, start, buffer.length - start)) != -1) {
            int fi = TokenFence.indexOf(buffer, 0, length - token.length(), first);
            if (fi == -1 || !TokenFence.isToken(buffer, fi, token)) {
                int fi2 = TokenFence.indexOf(buffer, length - token.length(), buffer.length, first);
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
                System.arraycopy(buffer, fi + token.length(), buffer, 0, token.length());
                start = 0;
            }
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

    private static boolean isToken(char[] buffer, int start, String token) {
        for (int i = 1; i < token.length(); i++) {
            if (buffer[start + i] != token.charAt(i)) {
                return false;
            }
        }

        return true;
    }
}
