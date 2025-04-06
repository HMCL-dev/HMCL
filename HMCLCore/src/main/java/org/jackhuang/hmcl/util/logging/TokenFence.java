package org.jackhuang.hmcl.util.logging;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
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

    private static final Charset CHARSET = StandardCharsets.UTF_16;

    public static void filter(String[] accessTokens, Reader reader, Writer out) throws IOException {
        char[] buffer = allocateBuffer(accessTokens[0]);
        if (accessTokens.length == 1) {
            filter(accessTokens[0], reader, out, buffer);
        } else {
            Path t1 = Files.createTempFile("hmcl-token-fence-", ".txt"), t2 = Files.createTempFile("hmcl-token-fence-", ".txt");
            try (Writer o1 = Files.newBufferedWriter(t1, CHARSET)) {
                filter(accessTokens[0], reader, o1, buffer);
            }

            for (int i = 1; i < accessTokens.length - 1; i++) {
                String token = accessTokens[i];
                if (token.length() > buffer.length) {
                    buffer = allocateBuffer(token);
                }

                try (Reader i1 = Files.newBufferedReader(t1, CHARSET); Writer i2 = Files.newBufferedWriter(t2, CHARSET)) {
                    filter(token, i1, i2, buffer);
                }

                Path t3 = t2;
                t2 = t1;
                t1 = t3;
            }

            try (Reader r1 = Files.newBufferedReader(t1, CHARSET)) {
                String token = accessTokens[accessTokens.length - 1];
                if (token.length() > buffer.length) {
                    buffer = allocateBuffer(token);
                }
                filter(token, r1, out, buffer);
            }

            Files.delete(t1);
            Files.delete(t2);
        }
    }

    private static char[] allocateBuffer(String token) {
        return new char[Math.max(1024, token.length() + 16)];
    }

    /**
     * The main logic for filtering tokens.
     * In every single cycle, we fill the buffer, do filtering and write the buffer to 'out'.
     */
    private static void filter(String token, Reader reader, Writer out, char[] buffer) throws IOException {
        if (buffer.length < token.length()) {
            throw new AssertionError(String.format("Buffer is too small to contain %d chars.", token.length()));
        }

        char first = token.charAt(0);
        // The [0, start) part is the un-processed data from the last cycle.
        int start = 0, length;
        while ((length = reader.read(buffer, start, buffer.length - start)) != -1 || start != 0) { // we have data remained in reader / buffer.
            if (start != 0) {
                length = (length == -1 ? 0 : length) + start; // Update length. [0, length) is filled with data.
            }
            if (length < token.length()) { // If the length is smaller than the token, write directly.
                out.write(buffer, 0, length);
                return;
            }

            // A buffer can be split into two parts: body region and tail region.
            // body region: [0, tail), where a possible complete token may appear.
            // Tail region: [tail, length).

            int tail = length - token.length() + 1, fi = findToken(buffer, tail, token);
            if (fi == -1) { // No complete token detected in body region.
                int fi2 = indexOf(buffer, tail, length, first); // Find the first char of the token in tail region.
                if (fi2 == -1) { // No token.
                    out.write(buffer, 0, length);
                    start = 0;
                } else { // The first char of the token is detected. Write parts before that char, and copy the unprocessed parts back for further process.
                    out.write(buffer, 0, fi2);
                    System.arraycopy(buffer, fi2, buffer, 0, length - fi2);
                    start = length - fi2;
                }
            } else { // A complete token is detected. Write buffer into 'out'.
                out.write(buffer, 0, fi);
                out.write("<access token>");
                start = length - fi - token.length();
                System.arraycopy(buffer, fi + token.length(), buffer, 0, start); // And copy the unprocessed parts back for further process.
            }
        }
    }

    /**
     * Find a token in buffer [0, tail + token) by finding the first character and check the remained parts.
     * @param buffer the buffer.
     * @param tail the tail index. First character of token should only be in [0, tail).
     * @param token the token
     * @return The index of first character. -1 if no token found.
     */
    private static int findToken(char[] buffer, int tail, String token) {
        char first = token.charAt(0);
        int start = 0;
        while (true) {
            int fi = indexOf(buffer, start, tail, first);
            if (fi == -1) {
                return -1;
            }
            if (isToken(buffer, fi, token)) {
                return fi;
            }
            start = fi + 1;
        }
    }

    /**
     * Find the first character in buffer from [start, tail).
     * @param buffer The buffer
     * @param start The start index
     * @param tail The tail index. (Note this is not 'length'!)
     * @param target The target character.
     * @return The index of target located in buffer. -1 if no targets are found.
     */
    private static int indexOf(char[] buffer, int start, int tail, char target) {
        for (int i = start; i < tail; i++) {
            if (buffer[i] == target) {
                return i;
            }
        }

        return -1;
    }

    /**
     * <p>Check whether the parts start in buffer from 'start' is matched with a token.</p>
     * <p> NOTE: Callers must make sure there is enough space for a token to appear.</p>
     */
    private static boolean isToken(char[] buffer, int start, String token) {
        for (int i = 1, length = token.length(); i < length; i++) {
            if (buffer[start + i] != token.charAt(i)) {
                return false;
            }
        }

        return true;
    }
}
