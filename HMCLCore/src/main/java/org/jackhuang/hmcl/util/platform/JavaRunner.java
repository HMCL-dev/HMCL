// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.io.JarUtils;

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

public final class JavaRunner {
    private JavaRunner() {
    }

    private static String maskCommandline(List<String> commandline) {
        return commandline.stream().map(str -> {
            if (str.startsWith("-D")) {
                int eqIdx = str.indexOf('=');
                if (eqIdx != -1) {
                    String key = str.substring(2, eqIdx);
                    String value = str.substring(eqIdx + 1);
                    if (key.contains("http.proxy") ||
                            key.startsWith("https.proxy") ||
                            key.startsWith("socksProxy") ||
                            key.equals("hmcl.microsoft.auth.id") ||
                            key.equals("hmcl.curseforge.apikey")
                    ) {
                        return "-D" + key + "=" + (value.isEmpty() ? "" : value.charAt(0) + "*".repeat(value.length() - 1));
                    }
                }
            }

            return str;
        }).collect(Collectors.joining(" "));
    }

    /// Starts the specified JAR in a new process using the default Java runtime.
    public static void runJava(Path jar, String... appArgs) throws IOException {
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
        LOG.info("Starting process: " + maskCommandline(commandline));
        new ProcessBuilder(commandline)
                .directory(Paths.get("").toAbsolutePath().toFile())
                .inheritIO()
                .start();
    }

    /// Restart the current application.
    public static void restartSelf() throws IOException {
        LOG.info("Restarting HMCL");
        Path jarPath = JarUtils.thisJarPath();
        if (jarPath == null)
            throw new IOException("Failed to find current HMCL location");

        runJava(jarPath);
    }
}
