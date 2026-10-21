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
import org.jetbrains.annotations.NotNullByDefault;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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
    /// The shared protocol output on raw stderr, also used to detect failures when writing messages.
    static private final PrintStream protocol = new PrintStream(new FileOutputStream(FileDescriptor.err), true, MonitorProtocol.CHARSET);

    private MonitorProtocol() {
    }

    /// Writes and flushes the handshake message carrying the game process id and its start time.
    static void pidMessage(long pid, long processStartTime) {
        protocol.println("P\t" + pid + "\t" + processStartTime);
    }

    /// Writes and flushes a game output line message.
    static void logMessage(boolean isErrorStream, String line) {
        protocol.println("L\t" + (isErrorStream ? 1 : 0) + "\t" + line);
    }

    /// Writes and flushes the game exit message carrying the exit code and its classified type.
    static void exitMessage(int exitCode, ProcessListener.ExitType exitType) {
        protocol.println("E\t" + exitCode + "\t" + exitType.name());
    }

    static boolean parentGone() {
        return protocol.checkError();
    }

    sealed interface Message permits Pid, Log, Exit {
        static Message decode(String message) throws IllegalArgumentException {
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
