/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.gradle.javafx;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jna.Platform;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Glavo
 */
public final class JavaFXUtils {
    public static final String[] MODULES = {"base", "graphics", "controls"};

    private static void addDependencies(Project rootProject) {
        try {
            Class.forName("javafx.application.Application", false, JavaFXUtils.class.getClassLoader());
            return;
        } catch (Throwable ignored) {
        }

        String os;
        if (Platform.isWindows())
            os = "windows";
        else if (Platform.isMac())
            os = "macos";
        else if (Platform.isLinux())
            os = "linux";
        else if (Platform.isFreeBSD())
            os = "freebsd";
        else
            return;

        String arch;
        if (Platform.isIntel())
            arch = Platform.is64Bit() ? "x86_64" : "x86";
        else if (Platform.isARM())
            arch = Platform.is64Bit() ? "arm64" : "arm32";
        else if (Platform.isLoongArch() && Platform.is64Bit())
            arch = "loongarch64";
        else if (Platform.isRISCV())
            arch = "riscv64";
        else
            return;

        JavaFXPlatform platform = JavaFXPlatform.ALL.get(os + "-" + arch);
        if (platform != null) {
            int featureVersion = Runtime.version().feature();

            String version;
            if (featureVersion >= 23)
                version = platform.getVersions().getOrDefault(JavaFXVersionType.MODERN, platform.getVersions().get(JavaFXVersionType.CLASSIC));
            else
                version = platform.getVersions().get(JavaFXVersionType.CLASSIC);

            if (version != null) {
                rootProject.subprojects(project -> {
                    for (String module : MODULES) {
                        String dependency = String.format("%s:javafx-%s:%s:%s", platform.getGroupId(), module, version, platform.getClassifier());
                        project.getDependencies().add("compileOnly", dependency);
                        project.getDependencies().add("testImplementation", dependency);
                    }
                });
            }
        }
    }

    private static void generateOpenJFXDependencies(Task task) {
        JsonObject dependenciesJson = new JsonObject();
        JavaFXPlatform.ALL.forEach((name, platform) -> {
            JsonObject platformJson = new JsonObject();
            platform.getVersions().forEach((versionType, version) -> {
                JsonArray modulesJson = new JsonArray();

                for (String module : MODULES) {
                    JsonObject moduleJson = new JsonObject();
                    moduleJson.addProperty("module", "javafx." + module);
                    moduleJson.addProperty("groupId", platform.getGroupId());
                    moduleJson.addProperty("artifactId", "javafx-" + module);
                    moduleJson.addProperty("version", version);
                    moduleJson.addProperty("classifier", platform.getClassifier());

                    String shaUrl = String.format("https://repo1.maven.org/maven2/%s/javafx-%s/%s/javafx-%s-%s-%s.jar.sha1",
                            platform.getGroupId().replace('.', '/'),
                            module, version,
                            module, version, platform.getClassifier()
                    );
                    task.getLogger().info("Fetching {}", shaUrl);
                    try (InputStream stream = new URI(shaUrl).toURL().openStream()) {
                        moduleJson.addProperty("sha1", new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim());
                    } catch (IOException | URISyntaxException e) {
                        throw new GradleException("Failed to fetch sha from " + shaUrl, e);
                    }

                    modulesJson.add(moduleJson);
                }

                platformJson.add(versionType.getName(), modulesJson);
            });
            dependenciesJson.add(name, platformJson);
        });

        Path outputFile = task.getProject().getRootProject().file("HMCL/src/main/resources/assets/openjfx-dependencies.json").toPath().toAbsolutePath().normalize();
        try {
            Files.createDirectories(outputFile.getParent());
            Files.writeString(outputFile, new GsonBuilder().setPrettyPrinting().create().toJson(dependenciesJson));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void preTouchOpenJFXDependencies(Task task) {
        String mirrorRepo = "https://mirrors.cloud.tencent.com/nexus/repository/maven-public";

        JavaFXPlatform.ALL.forEach((name, platform) -> {
            platform.getVersions().forEach((versionType, version) -> {
                for (String module : MODULES) {
                    String jarUrl = String.format("%s/%s/javafx-%s/%s/javafx-%s-%s-%s.jar",
                            mirrorRepo,
                            platform.getGroupId().replace('.', '/'),
                            module, version,
                            module, version, platform.getClassifier()
                    );
                    task.getLogger().info("Fetching {}", jarUrl);
                    try (InputStream stream = new URI(jarUrl).toURL().openStream()) {
                        stream.readNBytes(128);
                    } catch (IOException ignored) {
                    } catch (URISyntaxException e) {
                        throw new GradleException("Failed to fetch jar from " + jarUrl, e);
                    }
                }
            });
        });
    }

    public static void register(Project rootProject) {
        addDependencies(rootProject);

        rootProject.getTasks().register("generateOpenJFXDependencies", task -> task.doLast(JavaFXUtils::generateOpenJFXDependencies));
        rootProject.getTasks().register("preTouchOpenJFXDependencies", task -> task.doLast(JavaFXUtils::preTouchOpenJFXDependencies));
    }
}
