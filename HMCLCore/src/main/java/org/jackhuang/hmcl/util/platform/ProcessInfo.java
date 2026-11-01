// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// The information about a process that process listeners and UIs can rely on, regardless of
/// whether the process is owned by this JVM ([ManagedProcess]) or supervised by another process,
/// e.g. the HMCL monitor.
@NotNullByDefault
public interface ProcessInfo {

    /// The process id of the process.
    long pid();

    /// The handle of the process, or `null` if it cannot be resolved anymore, e.g. a dead
    /// process whose id has already been reaped by the operating system.
    @Nullable ProcessHandle handle();

    /// The command line of the process.
    List<String> commands();

    /// The classpath of the process, or `null` when unknown.
    @Nullable String classpath();

    /// The epoch millisecond at which the process started.
    long processStartTime();

    /// Returns whether the process is still running.
    boolean isRunning();

    /// Destroys the process.
    void stop();
}
