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

import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.ProcessInfo;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// A view of the game process launched by the HMCL monitor.
///
/// Unlike [ManagedProcess], this process may not be resolved when it exited before
/// monitor's handshake.
@NotNullByDefault
public final class MonitorGameProcess implements ProcessInfo {
    /// the handle of the game process, or `null` if it cannot be resolved.
    private final @Nullable ProcessHandle handle;
    /// the command line of the game process
    private final @Unmodifiable List<String> commands;
    /// the epoch millisecond at which the game process started
    private final long processStartTime;
    /// the monitor process
    private final Process monitor;
    /// the channel to send command to monitor. e.g. transfer responsibility for handling game exit / cancel launching
    private final MonitorController controller;

    /// Creates a view of the monitor-supervised game process.
    public MonitorGameProcess(@Nullable ProcessHandle handle, List<String> commands, long processStartTime, Process monitor) {
        this.handle = handle;
        this.commands = List.copyOf(commands);
        this.processStartTime = processStartTime;
        this.monitor = monitor;
        this.controller = new MonitorController(monitor);
    }

    /// Transfers exit handling to the monitor before the launcher begins shutting down.
    public void detach() {
        controller.detach();
    }

    /// Handles exit when monitor is attached and acknowledges that the exit callback has completed.
    public void handleExit(Runnable handler) {
        if (controller.isDetached())
            return;
        handler.run();
        controller.handled();
    }

    @Override
    public boolean isRunning() {
        return handle != null && handle.isAlive();
    }

    /// Requests cancellation over the control channel; the monitor owns game termination.
    @Override
    public void stop() {
        if (handle == null)
            return;
        Task.runAsync(() -> {
            if (!controller.cancel().await(MonitorProtocol.TIMEOUT, TimeUnit.MILLISECONDS))
                handle.destroy();
        }).whenComplete(exception -> {
            if (exception != null)
                handle.destroy();
        }).start();
    }

    @Override
    public @Nullable ProcessHandle handle() {
        return handle;
    }

    @Override
    public long pid() {
        return handle != null ? handle.pid() : -1;
    }

    @Override
    public @Unmodifiable List<String> commands() {
        return commands;
    }

    @Override
    public long processStartTime() {
        return processStartTime;
    }

    @Override
    public @Nullable String classpath() {
        return null;
    }

    public Process getMonitor() {
        return monitor;
    }

    /// Serializes cancellation and exit decisions onto monitor stdin.
    @NotNullByDefault
    private static final class MonitorController {
        /// UTF-8 control writer, kept open after DETACH so cancellation remains available.
        private final PrintStream writer;
        private final CountDownLatch exited = new CountDownLatch(1);
        /// Current owner of exit handling; all transitions are protected by this object's monitor.
        private State state = State.ATTACHED;

        /// Creates a control channel backed by the monitor process's stdin.
        MonitorController(Process monitor) {
            writer = new PrintStream(monitor.getOutputStream(), true, MonitorProtocol.CHARSET);
        }

        /// Sends DETACH exactly once, before launcher shutdown is scheduled.
        synchronized void detach() {
            if (state != State.ATTACHED)
                return;
            state = State.DETACHED;
            send(MonitorProtocol.ControlCommand.DETACH);
        }

        /// Returns whether the monitor owns exit handling.
        synchronized boolean isDetached() {
            return state == State.DETACHED;
        }

        /// Sends HANDLED only if the launcher still owns exit handling.
        synchronized void handled() {
            if (state != State.ATTACHED)
                return;
            state = State.HANDLED;
            exited.countDown();
            send(MonitorProtocol.ControlCommand.HANDLED);
            writer.close();
        }

        /// Asks the monitor to record cancellation before terminating the game.
        ///
        /// @return [CountDownLatch] when SUCCESSFULLY canceled
        synchronized CountDownLatch cancel() {
            return switch (state) {
                case ATTACHED, DETACHED -> {
                    send(MonitorProtocol.ControlCommand.CANCEL);
                    yield exited;
                }
                case HANDLED -> new CountDownLatch(0); // The game exit has already been handled.
            };
        }

        /// Writes and flushes one command while the caller holds the state lock.
        private void send(MonitorProtocol.ControlCommand command) {
            writer.println(command.name());
            if (writer.checkError())
                LOG.warning("Failed to send monitor control command: " + command);
        }

        /// Exit ownership is independent of cancellation, which is allowed until HANDLED.
        private enum State {
            /// The launcher owns exit handling and may cancel or transfer ownership.
            ATTACHED,
            /// The monitor owns exit handling; the launcher may still cancel the game.
            DETACHED,
            /// The launcher completed exit handling and closed the control pipe.
            HANDLED
        }
    }
}
