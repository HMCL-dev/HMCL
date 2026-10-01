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
public final class MonitorProtocol {

    /// The charset of the protocol stream.
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    /// How long to wait for the monitor's handshake before giving up, in milliseconds.
    static final long HANDSHAKE_TIMEOUT = 20_000;

    /// Message tag of the handshake message.
    static final String TAG_PID = "P";
    /// Message tag of a game output line.
    static final String TAG_LOG = "L";
    /// Message tag of the game exit message.
    static final String TAG_EXIT = "E";

    private MonitorProtocol() {
    }

    /// Builds the handshake message carrying the game process id and its start time.
    static String pidMessage(long pid, long processStartTime) {
        return TAG_PID + "\t" + pid + "\t" + processStartTime;
    }

    /// Builds a game output line message.
    static String logMessage(boolean isErrorStream, String line) {
        return TAG_LOG + "\t" + (isErrorStream ? 1 : 0) + "\t" + line;
    }

    /// Builds the game exit message carrying the exit code and its classified type.
    static String exitMessage(int exitCode, ProcessListener.ExitType exitType) {
        return TAG_EXIT + "\t" + exitCode + "\t" + exitType.name();
    }
}
