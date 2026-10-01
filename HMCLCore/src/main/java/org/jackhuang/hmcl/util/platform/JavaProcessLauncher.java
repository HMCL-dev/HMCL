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
package org.jackhuang.hmcl.util.platform;

import org.jackhuang.hmcl.java.JavaRuntime;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Starts new JVM processes for a jar file, inheriting the current JVM's arguments.
@NotNullByDefault
public final class JavaProcessLauncher {

    private JavaProcessLauncher() {
    }

    /// Builds the command line that starts a new JVM running the given jar with the given arguments.
    ///
    /// <p>The current JVM's {@code -D} and {@code -X} input arguments are inherited; when the
    /// Management API is not available, only the {@code hmcl.}-prefixed system properties are
    /// carried over as {@code -D} arguments.
    ///
    /// @param jar     the jar file to run
    /// @param appArgs the application arguments passed to the new process
    /// @return the command line, one argument per element
    public static List<String> buildJavaCommandLine(Path jar, String... appArgs) {
        List<String> commandline = new ArrayList<>();
        commandline.add(JavaRuntime.getDefault().getBinary().toString());

        try {
            for (String inputArgument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (inputArgument.startsWith("-D") || inputArgument.startsWith("-X")) {
                    commandline.add(inputArgument);
                }
            }
        } catch (Throwable ignored) {
            // ManagementFactory not available
            for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
                if (entry.getKey() instanceof String key && key.startsWith("hmcl.")) {
                    commandline.add("-D" + key + "=" + entry.getValue());
                }
            }
        }

        commandline.add("-jar");
        commandline.add(jar.toAbsolutePath().toString());
        commandline.addAll(Arrays.asList(appArgs));
        return commandline;
    }

    /// Masks the values of sensitive {@code -D} arguments in the given command line, so that it can
    /// be logged safely.
    ///
    /// @param commandline the command line, one argument per element
    /// @return the human-readable, masked single-line representation
    public static String maskCommandLine(List<String> commandline) {
        return commandline.stream().<String>map(str -> {
            if (str.startsWith("-D")) {
                int eqIdx = str.indexOf('=');
                if (eqIdx != -1) {
                    String key = str.substring(2, eqIdx);
                    String value = str.substring(eqIdx + 1);
                    if (key.contains("http.proxy") || key.startsWith("https.proxy") || key.startsWith("socksProxy")
                            || key.equals("hmcl.microsoft.auth.id") || key.equals("hmcl.curseforge.apikey")) {
                        return "-D" + key + "=" + (value.isEmpty() ? "" :
                                value.charAt(0) + "*".repeat(value.length() - 1));
                    }
                }
            }

            return str;
        }).collect(Collectors.joining(" "));
    }

    /// Starts a new JVM process running the given jar with the given arguments.
    ///
    /// <p>Fire-and-forget: the process inherits this process's I/O and is never waited for. The
    /// masked command line is written to the log before the process is started.
    ///
    /// @param jar     the jar file to run
    /// @param appArgs the application arguments passed to the new process
    /// @throws IOException if the process cannot be created
    public static void startJava(Path jar, String... appArgs) throws IOException {
        List<String> commandline = buildJavaCommandLine(jar, appArgs);
        LOG.info("Starting process: " + maskCommandLine(commandline));
        new ProcessBuilder(commandline)
                .directory(Paths.get("").toAbsolutePath().toFile())
                .inheritIO()
                .start();
    }
}
