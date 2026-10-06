/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.MinecraftVersionMatcher;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.addon.mod.ModRelationIndex;
import org.jackhuang.hmcl.addon.mod.NestedJarInspector.NestedJar;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jackhuang.hmcl.util.logging.Logger;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class LogExporter {
    private LogExporter() {
    }

    public static CompletableFuture<Void> exportLogs(
            Path zipFile, DefaultGameInstance instance, LaunchOptions options, String logs, String launchScript,
            PathMatcher logMatcher) {
        DefaultGameRepositorySnapshot repositorySnapshot = instance.getSnapshot();
        Path runDirectory = options.getGameDir();
        List<GameInstanceID> instances = new ArrayList<>();

        GameInstanceID currentInstanceId = instance.id;
        HashSet<GameInstanceID> resolvedSoFar = new HashSet<>();
        while (true) {
            if (resolvedSoFar.contains(currentInstanceId)) break;
            resolvedSoFar.add(currentInstanceId);
            @Nullable DefaultGameInstance currentInstance = repositorySnapshot.get(currentInstanceId);
            if (currentInstance == null)
                break;
            instances.add(currentInstanceId);

            if (currentInstance.getManifest().inheritsFrom() != null) {
                currentInstanceId = currentInstance.getManifest().inheritsFrom();
            } else {
                break;
            }
        }

        return CompletableFuture.runAsync(() -> {
            try (Zipper zipper = new Zipper(zipFile, true)) {
                processLogs(runDirectory.resolve("liteconfig"), "*.log", "liteconfig", zipper, logMatcher);
                processLogs(runDirectory.resolve("logs"), "*.log", "logs", zipper, logMatcher);
                processLogs(runDirectory, "*.log", "runDirectory", zipper, logMatcher);
                processLogs(runDirectory.resolve("crash-reports"), "*.txt", "crash-reports", zipper, logMatcher);

                zipper.putTextFile(LOG.getLogs(), "hmcl.log");
                zipper.putTextFile(logs, "minecraft.log");
                zipper.putTextFile(Logger.filterForbiddenToken(launchScript), OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS ? "launch.bat" : "launch.sh");

                try {
                    ModManager modManager = instance.getModManager();
                    modManager.refresh();
                    ModRelationIndex relationIndex = modManager.getRelationIndex();

                    List<LocalModFile> activeMods = modManager.getLocalFiles().stream()
                            .filter(LocalModFile::isActive)
                            .sorted((m1, m2) -> String.CASE_INSENSITIVE_ORDER.compare(m1.getName(), m2.getName()))
                            .toList();

                    StringBuilder infoBuilder = new StringBuilder();
                    infoBuilder.append("=== Mod Relation Analysis ===").append(System.lineSeparator())
                            .append("Complete: ").append(relationIndex.isComplete()).append(System.lineSeparator())
                            .append(System.lineSeparator())
                            .append("----------------------------").append(System.lineSeparator())
                            .append(System.lineSeparator());

                    LinkedHashSet<String> duplicates = new LinkedHashSet<>();
                    for (LocalModFile host : activeMods) {
                        Set<String> bundledIds = relationIndex.getBundledIds(host).stream()
                                .map(id -> id.toLowerCase(Locale.ROOT))
                                .collect(java.util.stream.Collectors.toSet());
                        if (bundledIds.isEmpty()) continue;
                        for (LocalModFile other : activeMods) {
                            if (other == host) continue;
                            for (String id : other.getProvidedIds()) {
                                if (bundledIds.contains(id.toLowerCase(Locale.ROOT))) {
                                    duplicates.add(other.getName() + " [" + other.getFileName()
                                            + "] <-> bundled in " + host.getName() + " (" + id + ")");
                                }
                            }
                        }
                    }

                    infoBuilder.append("=== Potential Duplicate Mods (installed separately AND bundled via Jar-in-Jar) ===").append(System.lineSeparator());
                    if (duplicates.isEmpty()) {
                        infoBuilder.append("None").append(System.lineSeparator());
                    } else {
                        infoBuilder.append("These mods may conflict with their bundled copies and cause crashes:").append(System.lineSeparator());
                        infoBuilder.append("(Matched by exact mod id against the scanned Jar-in-Jar tree.)").append(System.lineSeparator());
                        for (String line : duplicates) {
                            infoBuilder.append("\t|-> ").append(line).append(System.lineSeparator());
                        }
                    }
                    infoBuilder.append(System.lineSeparator())
                            .append("----------------------------").append(System.lineSeparator())
                            .append(System.lineSeparator());

                    String instanceMc = instance.getVersion().toString();
                    LinkedHashSet<String> incompatible = new LinkedHashSet<>();
                    if (StringUtils.isNotBlank(instanceMc)) {
                        for (LocalModFile host : activeMods) {
                            collectIncompatibleBundles(
                                    relationIndex.getBundledTree(host), instanceMc, host.getName(), incompatible);
                        }
                    }

                    infoBuilder.append("=== Incompatible Multi-Version Bundles (no copy for this instance's Minecraft version) ===").append(System.lineSeparator());
                    if (StringUtils.isBlank(instanceMc)) {
                        infoBuilder.append("Skipped: could not determine this instance's Minecraft version.").append(System.lineSeparator());
                    } else if (incompatible.isEmpty()) {
                        infoBuilder.append("None").append(System.lineSeparator());
                    } else {
                        infoBuilder.append("These bundled mods have no copy targeting MC ").append(instanceMc).append(" and likely fail to load:").append(System.lineSeparator());
                        for (String line : incompatible) {
                            infoBuilder.append("\t|-> ").append(line).append(System.lineSeparator());
                        }
                    }
                    infoBuilder.append(System.lineSeparator())
                            .append("----------------------------").append(System.lineSeparator())
                            .append(System.lineSeparator());

                    infoBuilder.append("=== Dependency Issues ===").append(System.lineSeparator());
                    if (relationIndex.getDependencyIssues().isEmpty()) {
                        infoBuilder.append("None").append(System.lineSeparator());
                    } else {
                        for (ModRelationIndex.DependencyIssue issue : relationIndex.getDependencyIssues()) {
                            String sourceName = issue.nestedSource() != null
                                    ? issue.nestedSource().displayName()
                                    : issue.declaringHost().getName();
                            infoBuilder.append("\t|-> ")
                                    .append(sourceName)
                                    .append(" requires ")
                                    .append(issue.resolution().dependency().id())
                                    .append(" ")
                                    .append(issue.resolution().dependency().versionConstraint())
                                    .append(": ")
                                    .append(issue.resolution().status());
                            String detectedVersions = issue.resolution().providers().stream()
                                    .map(ModRelationIndex.Provider::version)
                                    .filter(StringUtils::isNotBlank)
                                    .distinct()
                                    .collect(java.util.stream.Collectors.joining(", "));
                            if (!detectedVersions.isBlank()) {
                                infoBuilder.append(" (detected: ").append(detectedVersions).append(")");
                            }
                            infoBuilder.append(System.lineSeparator());
                        }
                    }
                    infoBuilder.append(System.lineSeparator())
                            .append("=== Active Conflicts ===").append(System.lineSeparator());
                    if (relationIndex.getActiveConflicts().isEmpty()) {
                        infoBuilder.append("None").append(System.lineSeparator());
                    } else {
                        for (ModRelationIndex.ActiveConflict conflict : relationIndex.getActiveConflicts()) {
                            String sourceName = conflict.nestedSource() != null
                                    ? conflict.nestedSource().displayName()
                                    : conflict.declaringHost().getName();
                            infoBuilder.append("\t|-> ")
                                    .append(sourceName)
                                    .append(" conflicts with ")
                                    .append(conflict.conflict().id())
                                    .append(" ")
                                    .append(conflict.conflict().versionConstraint())
                                    .append(conflict.conflict().hard() ? " [hard]" : " [warning]")
                                    .append(System.lineSeparator());
                        }
                    }
                    infoBuilder.append(System.lineSeparator())
                            .append("----------------------------").append(System.lineSeparator())
                            .append(System.lineSeparator());

                    infoBuilder.append("=== Mod List ===").append(System.lineSeparator());
                    infoBuilder.append("Filesystem structure of: ").append(runDirectory.resolve("mods")).append(System.lineSeparator());
                    infoBuilder.append("|-> mods").append(System.lineSeparator());

                    for (LocalModFile mod : activeMods) {
                        infoBuilder.append("|  |-> ").append(mod.getName());
                        if (StringUtils.isNotBlank(mod.getVersion()) && !"${version}".equals(mod.getVersion())) {
                            infoBuilder.append(" (").append(mod.getVersion()).append(")");
                        }
                        if (!mod.getName().equals(mod.getFileName())) {
                            infoBuilder.append(" [").append(mod.getFileName()).append("]");
                        }
                        infoBuilder.append(System.lineSeparator());
                    }

                    infoBuilder.append(System.lineSeparator())
                            .append("----------------------------").append(System.lineSeparator())
                            .append(System.lineSeparator())
                            .append("=== Jar-in-Jar Info List (active mods only) ===").append(System.lineSeparator());

                    boolean hasJij = false;
                    for (LocalModFile mod : activeMods) {
                        if (mod.hasBundledMods()) {
                            hasJij = true;
                            infoBuilder.append(mod.getName());
                            if (!mod.getName().equals(mod.getFileName())) {
                                infoBuilder.append(" [").append(mod.getFileName()).append("]");
                            }
                            infoBuilder.append(System.lineSeparator());
                            List<NestedJar> tree = relationIndex.getBundledTree(mod);
                            if (!tree.isEmpty()) {
                                appendJarTree(infoBuilder, tree, 1);
                            } else {
                                for (String bundled : mod.getBundledMods()) {
                                    String name = bundled.contains("/") ? bundled.substring(bundled.lastIndexOf('/') + 1) : bundled;
                                    infoBuilder.append("\t|-> ").append(name).append(System.lineSeparator());
                                }
                            }
                            infoBuilder.append(System.lineSeparator());
                        }
                    }

                    if (!hasJij) {
                        infoBuilder.append("No Jar-in-Jar info found").append(System.lineSeparator());
                    }

                    zipper.putTextFile(infoBuilder.toString(), "mods_info.txt");
                } catch (Exception e) {
                    LOG.warning("Failed to export mod info to crash report package", e);
                }

                for (GameInstanceID id : instances) {
                    @Nullable DefaultGameInstance currentInstance = repositorySnapshot.get(id);
                    if (currentInstance != null) {
                        try (var writer = new OutputStreamWriter(zipper.putStream(id + ".json"), StandardCharsets.UTF_8)) {
                            JsonUtils.GSON.toJson(currentInstance.getManifest(), writer);
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /// Collects multi-version groups that have no copy compatible with the instance.
    private static void collectIncompatibleBundles(List<NestedJar> siblings, String instanceMc, String hostName, LinkedHashSet<String> out) {
        Map<String, List<NestedJar>> byId = new LinkedHashMap<>();
        for (NestedJar node : siblings)
            if (StringUtils.isNotBlank(node.id()))
                byId.computeIfAbsent(node.id(), k -> new ArrayList<>()).add(node);
        for (Map.Entry<String, List<NestedJar>> e : byId.entrySet()) {
            List<NestedJar> copies = e.getValue();
            if (copies.size() > 1
                    && copies.stream().allMatch(NestedJar::minecraftConstraintRequired)
                    && copies.stream().noneMatch(n -> MinecraftVersionMatcher.matches(n, instanceMc)))
                out.add(e.getKey() + " (bundled in " + hostName + ", " + copies.size() + " versions, none for MC " + instanceMc + ")");
        }
        for (NestedJar node : siblings)
            collectIncompatibleBundles(node.children(), instanceMc, hostName, out);
    }

    /// Appends a Jar-in-Jar tree to the exported report.
    private static void appendJarTree(StringBuilder sb, List<NestedJar> nodes, int depth) {
        String indent = "\t".repeat(depth);
        for (NestedJar node : nodes) {
            sb.append(indent).append("|-> ").append(node.displayName());
            if (StringUtils.isNotBlank(node.id())) {
                sb.append(" (").append(node.id()).append(")");
            }
            if (StringUtils.isNotBlank(node.version())) {
                sb.append(" ").append(node.version());
            }
            if (StringUtils.isNotBlank(node.minecraftVersion())) {
                sb.append(" [MC ").append(node.minecraftVersion()).append("]");
            }
            sb.append(System.lineSeparator());
            if (node.hasChildren()) {
                appendJarTree(sb, node.children(), depth + 1);
            }
        }
    }

    private static void processLogs(Path directory, String fileExtension, String logDirectory, Zipper zipper, PathMatcher logMatcher) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, fileExtension)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    if (logMatcher == null || logMatcher.matches(file)) {
                        try (BufferedReader reader = IOUtils.newBufferedReaderMaybeNativeEncoding(file)) {
                            zipper.putLines(reader.lines().map(Logger::filterForbiddenToken), file.getFileName().toString());
                        } catch (IOException e) {
                            LOG.warning("Failed to read log file: " + file, e);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            LOG.warning("Failed to find any log on " + logDirectory, e);
        }
    }
}
