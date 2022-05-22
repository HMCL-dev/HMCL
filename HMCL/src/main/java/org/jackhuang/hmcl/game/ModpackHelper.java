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
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jackhuang.hmcl.mod.*;
import org.jackhuang.hmcl.mod.curse.CurseModpackProvider;
import org.jackhuang.hmcl.mod.mcbbs.McbbsModpackManifest;
import org.jackhuang.hmcl.mod.mcbbs.McbbsModpackProvider;
import org.jackhuang.hmcl.mod.modrinth.ModrinthModpackProvider;
import org.jackhuang.hmcl.mod.multimc.MultiMCInstanceConfiguration;
import org.jackhuang.hmcl.mod.multimc.MultiMCModpackProvider;
import org.jackhuang.hmcl.mod.server.ServerModpackManifest;
import org.jackhuang.hmcl.mod.server.ServerModpackProvider;
import org.jackhuang.hmcl.mod.server.ServerModpackRemoteInstallTask;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jackhuang.hmcl.util.function.ExceptionalRunnable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Lang.toIterable;
import static org.jackhuang.hmcl.util.Pair.pair;

public final class ModpackHelper {
    private ModpackHelper() {}

    private static final Map<String, ModpackProvider> providers = mapOf(
            pair(CurseModpackProvider.INSTANCE.getName(), CurseModpackProvider.INSTANCE),
            pair(McbbsModpackProvider.INSTANCE.getName(), McbbsModpackProvider.INSTANCE),
            pair(ModrinthModpackProvider.INSTANCE.getName(), ModrinthModpackProvider.INSTANCE),
            pair(MultiMCModpackProvider.INSTANCE.getName(), MultiMCModpackProvider.INSTANCE),
            pair(ServerModpackProvider.INSTANCE.getName(), ServerModpackProvider.INSTANCE),
            pair(HMCLModpackProvider.INSTANCE.getName(), HMCLModpackProvider.INSTANCE)
    );

    @Nullable
    public static ModpackProvider getProviderByType(String type) {
        return providers.get(type);
    }

    public static boolean isFileModpackByExtension(File file) {
        String ext = FileUtils.getExtension(file);
        return "zip".equals(ext) || "mrpack".equals(ext);
    }

    public static Modpack readModpackManifest(Path file, Charset charset) throws UnsupportedModpackException, ManuallyCreatedModpackException {
        try (ZipFile zipFile = CompressingUtils.openZipFile(file, charset)) {
            // Order for trying detecting manifest is necessary here.
            // Do not change to iterating providers.
            for (ModpackProvider provider : new ModpackProvider[]{
                    McbbsModpackProvider.INSTANCE,
                    CurseModpackProvider.INSTANCE,
                    ModrinthModpackProvider.INSTANCE,
                    HMCLModpackProvider.INSTANCE,
                    MultiMCModpackProvider.INSTANCE,
                    ServerModpackProvider.INSTANCE}) {
                try {
                    return provider.readManifest(zipFile, file, charset);
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
        }

        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(file, charset)) {
            findMinecraftDirectoryInManuallyCreatedModpack(file.toString(), fs);
            throw new ManuallyCreatedModpackException(file);
        } catch (IOException e) {
            // ignore it
        }

        throw new UnsupportedModpackException(file.toString());
    }

    public static Path findMinecraftDirectoryInManuallyCreatedModpack(String modpackName, FileSystem fs) throws IOException, UnsupportedModpackException {
        Path root = fs.getPath("/");
        if (isMinecraftDirectory(root)) return root;
        try (Stream<Path> firstLayer = Files.list(root)) {
            for (Path dir : toIterable(firstLayer)) {
                if (isMinecraftDirectory(dir)) return dir;

                try (Stream<Path> secondLayer = Files.list(dir)) {
                    for (Path subdir : toIterable(secondLayer)) {
                        if (isMinecraftDirectory(subdir)) return subdir;
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        throw new UnsupportedModpackException(modpackName);
    }

    private static boolean isMinecraftDirectory(Path path) {
        return Files.isDirectory(path.resolve("versions")) &&
                (path.getFileName() == null || ".minecraft".equals(FileUtils.getName(path)));
    }

    public static ModpackConfiguration<?> readModpackConfiguration(File file) throws IOException {
        if (!file.exists())
            throw new FileNotFoundException(file.getPath());
        else
            try {
                return JsonUtils.GSON.fromJson(FileUtils.readText(file), new TypeToken<ModpackConfiguration<?>>() {
                }.getType());
            } catch (JsonParseException e) {
                throw new IOException("Malformed modpack configuration");
            }
    }

    public static Task<?> getInstallTask(Profile profile, ServerModpackManifest manifest, String name, Modpack modpack) {
        profile.getRepository().markVersionAsModpack(name);

        ExceptionalRunnable<?> success = () -> {
            HMCLGameRepository repository = profile.getRepository();
            repository.refreshVersions();
            VersionSetting vs = repository.specializeVersionSetting(name);
            repository.undoMark(name);
            if (vs != null)
                vs.setGameDirType(GameDirectoryType.VERSION_FOLDER);
        };

        ExceptionalConsumer<Exception, ?> failure = ex -> {
            if (ex instanceof ModpackCompletionException && !(ex.getCause() instanceof FileNotFoundException)) {
                success.run();
                // This is tolerable and we will not delete the game
            }
        };

        return new ServerModpackRemoteInstallTask(profile.getDependency(), manifest, name)
                .whenComplete(Schedulers.defaultScheduler(), success, failure)
                .withStagesHint(Arrays.asList("hmcl.modpack", "hmcl.modpack.download"));
    }

    public static boolean isExternalGameNameConflicts(String name) {
        return Files.exists(Paths.get("externalgames").resolve(name));
    }

    public static Task<?> getInstallManuallyCreatedModpackTask(Profile profile, File zipFile, String name, Charset charset) {
        if (isExternalGameNameConflicts(name)) {
            throw new IllegalArgumentException("name existing");
        }

        return new ManuallyCreatedModpackInstallTask(profile, zipFile.toPath(), charset, name)
                .thenAcceptAsync(Schedulers.javafx(), location -> {
                    Profile newProfile = new Profile(name, location.toFile());
                    newProfile.setUseRelativePath(true);
                    Profiles.getProfiles().add(newProfile);
                    Profiles.setSelectedProfile(newProfile);
                });
    }

    public static Task<?> getInstallTask(Profile profile, File zipFile, String name, Modpack modpack) {
        profile.getRepository().markVersionAsModpack(name);

        ExceptionalRunnable<?> success = () -> {
            HMCLGameRepository repository = profile.getRepository();
            repository.refreshVersions();
            VersionSetting vs = repository.specializeVersionSetting(name);
            repository.undoMark(name);
            if (vs != null)
                vs.setGameDirType(GameDirectoryType.VERSION_FOLDER);
        };

        ExceptionalConsumer<Exception, ?> failure = ex -> {
            if (ex instanceof ModpackCompletionException && !(ex.getCause() instanceof FileNotFoundException)) {
                success.run();
                // This is tolerable and we will not delete the game
            }
        };

        if (modpack.getManifest() instanceof MultiMCInstanceConfiguration)
            return modpack.getInstallTask(profile.getDependency(), zipFile, name)
                    .whenComplete(Schedulers.defaultScheduler(), success, failure)
                    .thenComposeAsync(createMultiMCPostInstallTask(profile, (MultiMCInstanceConfiguration) modpack.getManifest(), name));
        else if (modpack.getManifest() instanceof McbbsModpackManifest)
            return modpack.getInstallTask(profile.getDependency(), zipFile, name)
                    .whenComplete(Schedulers.defaultScheduler(), success, failure)
                    .thenComposeAsync(createMcbbsPostInstallTask(profile, (McbbsModpackManifest) modpack.getManifest(), name));
        else
            return modpack.getInstallTask(profile.getDependency(), zipFile, name)
                    .whenComplete(Schedulers.javafx(), success, failure);
    }

    public static Task<Void> getUpdateTask(Profile profile, ServerModpackManifest manifest, Charset charset, String name, ModpackConfiguration<?> configuration) throws UnsupportedModpackException {
        switch (configuration.getType()) {
            case ServerModpackRemoteInstallTask.MODPACK_TYPE:
                return new ModpackUpdateTask(profile.getRepository(), name, new ServerModpackRemoteInstallTask(profile.getDependency(), manifest, name))
                        .withStagesHint(Arrays.asList("hmcl.modpack", "hmcl.modpack.download"));
            default:
                throw new UnsupportedModpackException();
        }
    }

    public static Task<?> getUpdateTask(Profile profile, File zipFile, Charset charset, String name, ModpackConfiguration<?> configuration) throws UnsupportedModpackException, ManuallyCreatedModpackException, MismatchedModpackTypeException {
        Modpack modpack = ModpackHelper.readModpackManifest(zipFile.toPath(), charset);
        ModpackProvider provider = getProviderByType(configuration.getType());
        if (provider == null) {
            throw new UnsupportedModpackException();
        }
        return provider.createUpdateTask(profile.getDependency(), name, zipFile, modpack);
    }

    public static void toVersionSetting(MultiMCInstanceConfiguration c, VersionSetting vs) {
        vs.setUsesGlobal(false);
        vs.setGameDirType(GameDirectoryType.VERSION_FOLDER);

        if (c.isOverrideJavaLocation()) {
            vs.setJavaDir(Lang.nonNull(c.getJavaPath(), ""));
        }

        if (c.isOverrideMemory()) {
            vs.setPermSize(Optional.ofNullable(c.getPermGen()).map(Object::toString).orElse(""));
            if (c.getMaxMemory() != null)
                vs.setMaxMemory(c.getMaxMemory());
            vs.setMinMemory(c.getMinMemory());
        }

        if (c.isOverrideCommands()) {
            vs.setWrapper(Lang.nonNull(c.getWrapperCommand(), ""));
            vs.setPreLaunchCommand(Lang.nonNull(c.getPreLaunchCommand(), ""));
        }

        if (c.isOverrideJavaArgs()) {
            vs.setJavaArgs(Lang.nonNull(c.getJvmArgs(), ""));
        }

        if (c.isOverrideConsole()) {
            vs.setShowLogs(c.isShowConsole());
        }

        if (c.isOverrideWindow()) {
            vs.setFullscreen(c.isFullscreen());
            if (c.getWidth() != null)
                vs.setWidth(c.getWidth());
            if (c.getHeight() != null)
                vs.setHeight(c.getHeight());
        }
    }

    private static Task<Void> createMultiMCPostInstallTask(Profile profile, MultiMCInstanceConfiguration manifest, String version) {
        return Task.runAsync(Schedulers.javafx(), () -> {
            VersionSetting vs = Objects.requireNonNull(profile.getRepository().specializeVersionSetting(version));
            ModpackHelper.toVersionSetting(manifest, vs);
        });
    }

    private static Task<Void> createMcbbsPostInstallTask(Profile profile, McbbsModpackManifest manifest, String version) {
        return Task.runAsync(Schedulers.javafx(), () -> {
            VersionSetting vs = Objects.requireNonNull(profile.getRepository().specializeVersionSetting(version));
            if (manifest.getLaunchInfo().getMinMemory() > vs.getMaxMemory())
                vs.setMaxMemory(manifest.getLaunchInfo().getMinMemory());
        });
    }
}
