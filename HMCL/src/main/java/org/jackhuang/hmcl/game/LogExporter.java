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
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jackhuang.hmcl.util.logging.Logger;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class LogExporter {
    private LogExporter() {
    }

    public static CompletableFuture<Void> exportLogs(
            Path zipFile, DefaultGameRepository gameRepository, String versionId, String logs, String launchScript,
            PathMatcher logMatcher) {
        Path runDirectory = gameRepository.getRunDirectory(versionId);
        Path baseDirectory = gameRepository.getBaseDirectory();
        List<String> versions = new ArrayList<>();

        String currentVersionId = versionId;
        HashSet<String> resolvedSoFar = new HashSet<>();
        while (true) {
            if (resolvedSoFar.contains(currentVersionId)) break;
            resolvedSoFar.add(currentVersionId);
            Version currentVersion = gameRepository.getVersion(currentVersionId);
            versions.add(currentVersionId);

            if (StringUtils.isNotBlank(currentVersion.getInheritsFrom())) {
                currentVersionId = currentVersion.getInheritsFrom();
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
                    ModManager modManager = gameRepository.getModManager(versionId);
                    modManager.refresh();

                    List<LocalModFile> activeMods = modManager.getLocalFiles().stream()
                            .filter(LocalModFile::isActive)
                            .sorted((m1, m2) -> String.CASE_INSENSITIVE_ORDER.compare(m1.getName(), m2.getName()))
                            .toList();

                    StringBuilder infoBuilder = new StringBuilder();

                    // Mods that exist both as a standalone file and inside another active mod's
                    // Jar-in-Jar payload. Such duplicates can conflict and crash the game, so list
                    // them at the very top of the report.
                    LinkedHashSet<String> duplicates = new LinkedHashSet<>();
                    for (LocalModFile host : activeMods) {
                        if (!host.hasBundledMods()) continue;
                        for (String bundled : host.getBundledMods()) {
                            String bundledName = bundled.contains("/") ? bundled.substring(bundled.lastIndexOf('/') + 1) : bundled;
                            String bundledLower = bundledName.toLowerCase(Locale.ROOT);
                            String bundledNorm = bundledLower.replace("-", "").replace("_", "");
                            for (LocalModFile other : activeMods) {
                                if (other == host) continue;
                                String id = other.getId();
                                if (id == null || id.length() < 3) continue;
                                String idLower = id.toLowerCase(Locale.ROOT);
                                if (bundledLower.contains(idLower)
                                        || bundledNorm.contains(idLower.replace("-", "").replace("_", ""))) {
                                    duplicates.add(other.getName() + " [" + other.getFileName() + "] <-> bundled in " + host.getName() + " (" + bundledName + ")");
                                }
                            }
                        }
                    }

                    infoBuilder.append("=== Potential Duplicate Mods (installed separately AND bundled via Jar-in-Jar) ===").append(System.lineSeparator());
                    if (duplicates.isEmpty()) {
                        infoBuilder.append("None").append(System.lineSeparator());
                    } else {
                        infoBuilder.append("These mods may conflict with their bundled copies and cause crashes:").append(System.lineSeparator());
                        infoBuilder.append("(Heuristic: matched by mod id appearing in the bundled jar file name; may include false positives.)").append(System.lineSeparator());
                        for (String line : duplicates) {
                            infoBuilder.append("\t|-> ").append(line).append(System.lineSeparator());
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
                            for (String bundled : mod.getBundledMods()) {
                                String name = bundled.contains("/") ? bundled.substring(bundled.lastIndexOf('/') + 1) : bundled;
                                infoBuilder.append("\t|-> ").append(name).append(System.lineSeparator());
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

                for (String id : versions) {
                    Path versionJson = baseDirectory.resolve("versions").resolve(id).resolve(id + ".json");
                    if (Files.exists(versionJson)) {
                        zipper.putFile(versionJson, id + ".json");
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
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
