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
package org.jackhuang.hmcl.monitor;

import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.util.Lang;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// The line-based protocol spoken on the monitor process's stderr.
///
/// <p>Messages are UTF-8 encoded, one per line, with tab-separated fields:
/// <ul>
///     <li>{@code P\t<pid>\t<startTime>} &mdash; handshake, sent once the game process has been created;</li>
///     <li>{@code L\t<isError>\t<line>} &mdash; one line of game output;</li>
///     <li>{@code E\t<exitCode>\t<exitType>} &mdash; the game process has exited.</li>
/// </ul>
@NotNullByDefault
final class MonitorProtocol {
    /// The charset of the protocol stream.
    static final Charset CHARSET = StandardCharsets.UTF_8;
    /// How long to wait for the monitor's handshake before giving up, in milliseconds.
    static final long HANDSHAKE_TIMEOUT = 20_000;

    private MonitorProtocol() {
    }

    /// Writes and flushes the handshake message carrying the game process id and its start time.
    static void pidMessage(PrintStream protocol, long pid, long processStartTime) {
        protocol.println("P\t" + pid + "\t" + processStartTime);
    }

    /// Writes and flushes a game output line message.
    static void logMessage(PrintStream protocol, boolean isErrorStream, String line) {
        protocol.println("L\t" + (isErrorStream ? 1 : 0) + "\t" + line);
    }

    /// Writes and flushes the game exit message carrying the exit code and its classified type.
    static void exitMessage(PrintStream protocol, int exitCode, ProcessListener.ExitType exitType) {
        protocol.println("E\t" + exitCode + "\t" + exitType.name());
    }

    static void readAll(BufferedReader reader, Consumer<Message> messageConsumer, BiConsumer<String, IllegalArgumentException> err) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            try {
                messageConsumer.accept(decode(line));
            } catch (IllegalArgumentException e) {
                err.accept(line, e);
            }
        }
    }

    /// @throws IOException if launcher cannot shake hand with monitor within [MonitorProtocol#HANDSHAKE_TIMEOUT]
    static Pid readHandshake(BufferedReader reader, Process monitor) throws IOException {
        CompletableFuture<Pid> future = new CompletableFuture<>();
        Thread waiter = Lang.thread(() -> {
            try {
                String line = reader.readLine();
                if (line == null)
                    throw new IOException("The HMCL monitor exited before the handshake");
                if (decode(line) instanceof Pid pid) {
                    future.complete(pid);
                    return;
                }
                throw new IOException("Unexpected handshake message from HMCL monitor: " + line);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, "hmcl-monitor-handshake", true);

        boolean handshakeCompleted = false;
        try {
            Pid handshake = future.get(HANDSHAKE_TIMEOUT, TimeUnit.MILLISECONDS);
            handshakeCompleted = true;
            return handshake;
        } catch (TimeoutException e) {
            throw new IOException("Timed out waiting for the HMCL monitor handshake", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for the HMCL monitor handshake", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IOException(cause.getMessage(), cause);
        } finally {
            waiter.interrupt();
            if (!handshakeCompleted)
                monitor.destroy();
        }
    }

    public static Message decode(String message) throws IllegalArgumentException {
        String[] parts = message.split("\t", 3);
        try {
            return switch (parts[0]) {
                case "P" -> new Pid(Long.parseLong(parts[1]), Long.parseLong(parts[2]));
                case "L" -> new Log("1".equals(parts[1]), parts[2]);
                case "E" -> new Exit(Integer.parseInt(parts[1]), ProcessListener.ExitType.valueOf(parts[2]));
                default -> throw new IllegalStateException("Unexpected value: " + message.charAt(0));
            };
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    sealed interface Message permits Pid, Log, Exit {
    }

    /// handshake message
    record Pid(long pid, long processStartTime) implements Message {
    }

    /// game output line
    record Log(boolean isErrorStream, String line) implements Message {
    }

    /// game exit message
    record Exit(int exitCode, ProcessListener.ExitType exitType) implements Message {
    }
}
