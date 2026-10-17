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
package org.jackhuang.hmcl.util.io;

import org.jackhuang.hmcl.java.JavaRuntime;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.*;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class JarUtils {
    private JarUtils() {
    }

    private static final Path THIS_JAR;
    private static final Properties properties = new Properties();

    static {
        Class<?> entryPointClass = null;
        CodeSource cs = null;
        try {
            entryPointClass = Class.forName("org.jackhuang.hmcl.EntryPoint");
            cs = entryPointClass.getProtectionDomain().getCodeSource();
        } catch (ClassNotFoundException ignored) {
        }

        if (cs == null) {
            THIS_JAR = null;
        } else {
            Path path;
            try {
                path = Path.of(cs.getLocation().toURI()).toAbsolutePath();
            } catch (FileSystemNotFoundException | IllegalArgumentException | URISyntaxException e) {
                path = null;
            }
            THIS_JAR = path != null && Files.isRegularFile(path) ? path : null;
        }

        if (entryPointClass != null) {
            InputStream input = entryPointClass.getResourceAsStream("/assets/hmcl.properties");
            if (input != null) {
                try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /// Get the jar location of current application
    @Nullable
    public static Path tryGetThisJarPath() {
        return THIS_JAR;
    }

    /// Get the jar location of current application
    public static Path getThisJarPath() throws IOException {
        if (THIS_JAR == null)
            throw new IOException("Failed to find current HMCL location");
        return THIS_JAR;
    }

    public static String getAttribute(String name, String defaultValue) {
        return properties.getProperty(name, defaultValue);
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
        runJava(getThisJarPath());
    }

}
