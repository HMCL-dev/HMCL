// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

import org.jackhuang.hmcl.java.JavaRuntime;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests external command cancellation.
@NotNullByDefault
final class SystemUtilsTest {
    /// Cancellation returns only after the direct child exits.
    @Test
    void cancellingRunningCommandWaitsForItsExit(@TempDir Path directory) throws Exception {
        ProcessHandle parent = ProcessHandle.current();
        Path classes = Path.of(WaitingCommand.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        ProcessBuilder command = new ProcessBuilder(JavaRuntime.getDefault().getBinary().toString(), "-cp", classes.toString(),
                WaitingCommand.class.getName(), directory.toString());
        var child = new AtomicReference<@Nullable ProcessHandle>();
        try {
            assertThrows(CancellationException.class, () -> SystemUtils.callExternalProcess(command, () -> {
                try (Stream<ProcessHandle> children = parent.children()) {
                    @Nullable ProcessHandle ready = children.filter(process -> Files.exists(directory.resolve(Long.toString(process.pid()))))
                            .findFirst().orElse(null);
                    if (ready != null) {
                        child.set(ready);
                        throw new CancellationException();
                    }
                }
            }));
            assertFalse(Objects.requireNonNull(child.get()).isAlive());
        } finally {
            @Nullable ProcessHandle process = child.get();
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.onExit().join();
            }
        }
    }

    /// A child JVM that signals readiness and exits on its own if cancellation is never checked.
    @NotNullByDefault
    public static final class WaitingCommand {
        /// Publishes the PID as a filename so the parent can recognize this live command.
        public static void main(String[] args) throws Exception {
            Files.createFile(Path.of(args[0], Long.toString(ProcessHandle.current().pid())));
            TimeUnit.SECONDS.sleep(30);
        }
    }
}
