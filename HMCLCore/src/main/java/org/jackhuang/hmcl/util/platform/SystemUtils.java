/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.platform;

import org.jackhuang.hmcl.java.JavaRuntime;
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

    public static String run(String... command) throws Exception {
        return run(Arrays.asList(command),
                inputStream -> IOUtils.readFullyAsString(inputStream, OperatingSystem.NATIVE_CHARSET));
    }

    public static <T> T run(List<String> command, ExceptionalFunction<InputStream, T, ?> convert) throws Exception {
        File nul = OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS
                ? new File("NUL")
                : new File("/dev/null");

        Process process = new ProcessBuilder(command)
                .redirectInput(nul)
                .redirectError(nul)
                .start();
        try {
            InputStream inputStream = process.getInputStream();
            CompletableFuture<T> future = CompletableFuture.supplyAsync(
                    Lang.wrap(() -> convert.apply(inputStream)),
                    Schedulers.io());

            if (!process.waitFor(15, TimeUnit.SECONDS))
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
        return JavaRuntime.CURRENT_VERSION >= 9 && Thread.currentThread().getContextClassLoader().getResource("com/sun/tools/attach/VirtualMachine.class") != null;
    }

    private static void onLogLine(String log) {
        LOG.info(log);
    }
}
