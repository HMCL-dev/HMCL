// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.launch;

import org.jackhuang.hmcl.game.GcStrategy;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/// A launch-stopping GC compatibility error, distinguished from an inconclusive or failed runtime probe.
@NotNullByDefault
public final class GcStrategyException extends IOException {
    /// User-facing failure categories; only confirmed collector rejection is reported as unsupported.
    @NotNullByDefault
    public enum Reason {
        /// No supported mode of the explicitly selected collector is available for the selected Java.
        UNSUPPORTED,
        /// The executable could not be run, or Java failed for a reason unrelated to collector availability.
        DETECTION_FAILED,
        /// The selected Java did not finish its capability check within the time limit.
        DETECTION_TIMED_OUT
    }

    /// The reason the launch was stopped.
    private final Reason reason;
    /// The requested collector preference, retained even when detection fails.
    private final GcStrategy gcStrategy;
    /// Full version of the Java runtime selected for the game.
    private final String javaVersion;
    /// Actual Java executable selected for the game, without substitution.
    private final Path executable;

    /// Creates a collector-specific failure while retaining technical diagnostics in the cause for logs.
    public GcStrategyException(Reason reason, GcStrategy gcStrategy, String javaVersion, Path executable, @Nullable Throwable cause) {
        super("GC strategy check failed: " + reason + ", strategy=" + gcStrategy
                + ", Java=" + javaVersion + ", executable=" + executable, cause);
        this.reason = reason;
        this.gcStrategy = gcStrategy;
        this.javaVersion = javaVersion;
        this.executable = executable;
    }

    /// Returns the user-facing failure category.
    public Reason getReason() {
        return reason;
    }

    /// Returns the requested collector so the error dialog identifies the correct strategy.
    public GcStrategy getGcStrategy() {
        return gcStrategy;
    }

    /// Returns the full version of the selected game Java.
    public String getJavaVersion() {
        return javaVersion;
    }

    /// Returns the executable whose collector availability was checked.
    public Path getExecutable() {
        return executable;
    }
}
