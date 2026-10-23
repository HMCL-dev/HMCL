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
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@NotNullByDefault
final class MonitorProtocol {
    /// The charset of the protocol stream.
    static final Charset CHARSET = StandardCharsets.UTF_8;
    /// How long to wait for the monitor's handshake before giving up, in milliseconds.
    static final long TIMEOUT = 20_000;

    private MonitorProtocol() {
    }

    /// read and parse control from launcher forever
    static void readLauncherControl(BufferedReader control, Consumer<ControlCommand> commandConsumer) throws IOException {
        @Nullable String line;
        while ((line = control.readLine()) != null) {
            try {
                commandConsumer.accept(ControlCommand.valueOf(line));
            } catch (IllegalArgumentException e) {
                throw new IOException("Unexpected launcher control message: " + line, e);
            }
        }
    }

    /// read and parse message from monitor forever
    static void readMonitorMessage(BufferedReader reader, Consumer<MonitorMessage> messageConsumer) throws IOException {
        @Nullable String line;
        while ((line = reader.readLine()) != null) {
            try {
                messageConsumer.accept(MonitorMessage.valueOf(line));
            } catch (IllegalArgumentException e) {
                // ignore regular err messages
            }
        }
    }

    /// read handshake (pid message) from monitor
    ///
    /// @throws IOException if launcher cannot shake hand with monitor within [MonitorProtocol#TIMEOUT]
    static MonitorMessage.Pid readHandshake(BufferedReader reader, Runnable failClean) throws IOException {
        CompletableFuture<MonitorMessage.Pid> future = new CompletableFuture<>();
        Thread waiter = Lang.thread(() -> {
            try {
                @Nullable String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        if (MonitorMessage.valueOf(line) instanceof MonitorMessage.Pid pid) {
                            future.complete(pid);
                            return;
                        }
                        throw new IOException("HMCL monitor sent message before handshake: " + line);
                    } catch (IllegalArgumentException e) {
                        // ignore regular err messages
                    }
                }
                throw new IOException("The HMCL monitor exited before handshake");
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, "hmcl-monitor-handshake", true);

        boolean handshakeCompleted = false;
        try {
            MonitorMessage.Pid handshake = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
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
            if (!handshakeCompleted) {
                failClean.run();
                waiter.interrupt();
            }
        }
    }

    /// Commands sent from the launcher to monitor stdin, serialized using their enum names.
    enum ControlCommand {
        /// Request cancellation before the monitor terminates the game.
        CANCEL,
        /// Transfer exit handling to the monitor while keeping cancellation available.
        DETACH,
        /// Confirm that the launcher has finished handling game exit.
        HANDLED
    }

    /// Messages sent from the monitor stderr to launcher, serialized using [encode()].
    sealed interface MonitorMessage permits MonitorMessage.Pid, MonitorMessage.Log, MonitorMessage.Exit {
        static MonitorMessage valueOf(String message) throws IllegalArgumentException {
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

        String encode();

        /// handshake message
        record Pid(long pid, long processStartTime) implements MonitorMessage {
            @Override
            public String encode() {
                return "P\t" + pid + "\t" + processStartTime;
            }
        }

        /// game output line
        record Log(boolean isErrorStream, String line) implements MonitorMessage {
            @Override
            public String encode() {
                return "L\t" + (isErrorStream ? 1 : 0) + "\t" + line;
            }
        }

        /// game exit message
        record Exit(int exitCode, ProcessListener.ExitType exitType) implements MonitorMessage {
            @Override
            public String encode() {
                return "E\t" + exitCode + "\t" + exitType.name();
            }
        }
    }
}
