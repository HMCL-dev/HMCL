// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class SystemUtils {
    private SystemUtils() {
    }

    public static @Nullable Path which(String command) {
        String path = System.getenv("PATH");
        if (path == null)
            return null;

        try {
            for (String item : path.split(File.pathSeparator)) {
                try {
                    Path program = Paths.get(item, command);
                    if (Files.isExecutable(program))
                        return program.toRealPath();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    public static int callExternalProcess(String... command) throws IOException, InterruptedException {
        return callExternalProcess(Arrays.asList(command));
    }

    public static int callExternalProcess(List<String> command) throws IOException, InterruptedException {
        return callExternalProcess(new ProcessBuilder(command));
    }

    public static int callExternalProcess(ProcessBuilder processBuilder) throws IOException, InterruptedException {
        ManagedProcess managedProcess = new ManagedProcess(processBuilder);
        managedProcess.pumpInputStream(SystemUtils::onLogLine);
        managedProcess.pumpErrorStream(SystemUtils::onLogLine);
        return managedProcess.getProcess().waitFor();
    }

    private static final Duration DEFAULT_MAX_WAIT_TIME = Duration.ofSeconds(15);

    public static String run(String... command) throws Exception {
        return run(List.of(command), DEFAULT_MAX_WAIT_TIME);
    }

    public static String run(List<String> command, Duration maxWaitTime) throws Exception {
        return run(command, inputStream -> IOUtils.readFullyAsString(inputStream, OperatingSystem.NATIVE_CHARSET), maxWaitTime);
    }

    public static <T> T run(List<String> command, ExceptionalFunction<InputStream, T, ?> convert) throws Exception {
        return run(command, convert, DEFAULT_MAX_WAIT_TIME);
    }

    public static <T> T run(List<String> command, ExceptionalFunction<InputStream, T, ?> convert, Duration maxWaitTime) throws Exception {
        Process process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            InputStream inputStream = process.getInputStream();
            CompletableFuture<T> future = CompletableFuture.supplyAsync(
                    Lang.wrap(() -> convert.apply(inputStream)),
                    Schedulers.io());

            if (!process.waitFor(maxWaitTime.toMillis(), TimeUnit.MILLISECONDS))
                throw new TimeoutException();

            if (process.exitValue() != 0)
                throw new IOException("Bad exit code: " + process.exitValue());

            return future.get();
        } finally {
            if (process.isAlive())
                process.destroy();
        }
    }

    public static boolean supportJVMAttachment() {
        return Thread.currentThread().getContextClassLoader().getResource("com/sun/tools/attach/VirtualMachine.class") != null;
    }

    public static void onLogLine(String log) {
        LOG.info(log);
    }
}
