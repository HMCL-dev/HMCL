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
package org.jackhuang.hmcl.mod.mcbbs;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

public class McbbsModpackCompletionTask extends Task<Void> {

    private final DefaultGameRepository repository;
    private final String version;
    private ModpackConfiguration<McbbsModpackManifest> manifest;
    private GetTask dependent;
    private McbbsModpackManifest remoteManifest;
    private final List<Task<?>> dependencies = new LinkedList<>();

    public McbbsModpackCompletionTask(DefaultDependencyManager dependencyManager, String version) {
        this(dependencyManager, version, null);
    }

    public McbbsModpackCompletionTask(DefaultDependencyManager dependencyManager, String version, ModpackConfiguration<McbbsModpackManifest> manifest) {
        this.repository = dependencyManager.getGameRepository();
        this.version = version;

        if (manifest == null) {
            try {
                File manifestFile = repository.getModpackConfiguration(version);
                if (manifestFile.exists()) {
                    this.manifest = JsonUtils.GSON.fromJson(FileUtils.readText(manifestFile), new TypeToken<ModpackConfiguration<McbbsModpackManifest>>() {
                    }.getType());
                }
            } catch (Exception e) {
                Logging.LOG.log(Level.WARNING, "Unable to read mcbbs modpack manifest.json", e);
            }
        } else {
            this.manifest = manifest;
        }
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        if (manifest == null || StringUtils.isBlank(manifest.getManifest().getFileApi())) return;
        dependent = new GetTask(new URL(manifest.getManifest().getFileApi() + "/manifest.json"));
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return dependent == null ? Collections.emptySet() : Collections.singleton(dependent);
    }

    @Override
    public void execute() throws Exception {
        if (manifest == null || StringUtils.isBlank(manifest.getManifest().getFileApi())) return;

        try {
            remoteManifest = JsonUtils.fromNonNullJson(dependent.getResult(), McbbsModpackManifest.class);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }

        Path rootPath = repository.getVersionRoot(version).toPath();

        // Because in China, Curse is too difficult to visit,
        // if failed, ignore it and retry next time.
//        CurseManifest newManifest = manifest.setFiles(
//                manifest.getFiles().parallelStream()
//                        .map(file -> {
//                            updateProgress(finished.incrementAndGet(), manifest.getFiles().size());
//                            if (StringUtils.isBlank(file.getFileName())) {
//                                try {
//                                    return file.withFileName(NetworkUtils.detectFileName(file.getUrl()));
//                                } catch (IOException e) {
//                                    try {
//                                        String result = NetworkUtils.doGet(NetworkUtils.toURL(String.format("https://cursemeta.dries007.net/%d/%d.json", file.getProjectID(), file.getFileID())));
//                                        CurseMetaMod mod = JsonUtils.fromNonNullJson(result, CurseMetaMod.class);
//                                        return file.withFileName(mod.getFileNameOnDisk()).withURL(mod.getDownloadURL());
//                                    } catch (FileNotFoundException fof) {
//                                        Logging.LOG.log(Level.WARNING, "Could not query cursemeta for deleted mods: " + file.getUrl(), fof);
//                                        notFound.set(true);
//                                        return file;
//                                    } catch (IOException | JsonParseException e2) {
//                                        try {
//                                            String result = NetworkUtils.doGet(NetworkUtils.toURL(String.format("https://addons-ecs.forgesvc.net/api/v2/addon/%d/file/%d", file.getProjectID(), file.getFileID())));
//                                            CurseMetaMod mod = JsonUtils.fromNonNullJson(result, CurseMetaMod.class);
//                                            return file.withFileName(mod.getFileName()).withURL(mod.getDownloadURL());
//                                        } catch (FileNotFoundException fof) {
//                                            Logging.LOG.log(Level.WARNING, "Could not query forgesvc for deleted mods: " + file.getUrl(), fof);
//                                            notFound.set(true);
//                                            return file;
//                                        } catch (IOException | JsonParseException e3) {
//                                            Logging.LOG.log(Level.WARNING, "Unable to fetch the file name of URL: " + file.getUrl(), e);
//                                            Logging.LOG.log(Level.WARNING, "Unable to fetch the file name of URL: " + file.getUrl(), e2);
//                                            Logging.LOG.log(Level.WARNING, "Unable to fetch the file name of URL: " + file.getUrl(), e3);
//                                            allNameKnown.set(false);
//                                            return file;
//                                        }
//                                    }
//                                }
//                            } else {
//                                return file;
//                            }
//                        })
//                        .collect(Collectors.toList()));
//
//        Map<String, ModpackConfiguration.FileInformation> files = manifest.getManifest().getFiles().stream()
//                .collect(Collectors.toMap(ModpackConfiguration.FileInformation::getPath,
//                        Function.identity()));
//
//        Set<String> remoteFiles = remoteManifest.getFiles().stream().map(ModpackConfiguration.FileInformation::getPath)
//                .collect(Collectors.toSet());
//
//        // for files in new modpack
//        for (ModpackConfiguration.FileInformation file : remoteManifest.getFiles()) {
//            Path actualPath = rootPath.resolve(file.getPath());
//            boolean download;
//            if (!files.containsKey(file.getPath())) {
//                // If old modpack does not have this entry, download it
//                download = true;
//            } else if (!Files.exists(actualPath)) {
//                // If both old and new modpacks have this entry, but the file is missing...
//                // Re-download it since network problem may cause file missing
//                download = true;
//            } else {
//                // If user modified this entry file, we will not replace this file since this modified file is that user expects.
//                String fileHash = encodeHex(digest("SHA-1", actualPath));
//                String oldHash = files.get(file.getPath()).getHash();
//                download = !Objects.equals(oldHash, file.getHash()) && Objects.equals(oldHash, fileHash);
//            }
//
//            if (download) {
//                dependencies.add(new FileDownloadTask(
//                        new URL(remoteManifest.getFileApi() + "/overrides/" + NetworkUtils.encodeLocation(file.getPath())),
//                        actualPath.toFile(),
//                        new FileDownloadTask.IntegrityCheck("SHA-1", file.getHash())));
//            }
//        }
//
//        // If old modpack have this entry, and new modpack deleted it. Delete this file.
//        for (ModpackConfiguration.FileInformation file : manifest.getManifest().getFiles()) {
//            Path actualPath = rootPath.resolve(file.getPath());
//            if (Files.exists(actualPath) && !remoteFiles.contains(file.getPath()))
//                Files.deleteIfExists(actualPath);
//        }
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
//        if (manifest == null || StringUtils.isBlank(manifest.getManifest().getFileApi())) return;
//        File manifestFile = repository.getModpackConfiguration(version);
//        FileUtils.writeText(manifestFile, JsonUtils.GSON.toJson(new ModpackConfiguration<>(remoteManifest, this.manifest.getType(), this.manifest.getName(), this.manifest.getVersion(), remoteManifest.getFiles())));
    }
}
