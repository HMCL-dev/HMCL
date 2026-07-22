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
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.modpack.*;
import org.jackhuang.hmcl.modpack.curse.CurseModpackProvider;
import org.jackhuang.hmcl.modpack.mcbbs.McbbsModpackManifest;
import org.jackhuang.hmcl.modpack.mcbbs.McbbsModpackProvider;
import org.jackhuang.hmcl.modpack.modrinth.ModrinthModpackProvider;
import org.jackhuang.hmcl.modpack.multimc.MultiMCComponents;
import org.jackhuang.hmcl.modpack.multimc.MultiMCInstanceConfiguration;
import org.jackhuang.hmcl.modpack.multimc.MultiMCModpackProvider;
import org.jackhuang.hmcl.modpack.server.ServerModpackManifest;
import org.jackhuang.hmcl.modpack.server.ServerModpackProvider;
import org.jackhuang.hmcl.modpack.server.ServerModpackRemoteInstallTask;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.GameWindowType;
import org.jackhuang.hmcl.setting.JavaVersionType;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;

import org.jackhuang.hmcl.util.PortablePath;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jackhuang.hmcl.util.function.ExceptionalRunnable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Lang.toIterable;
import static org.jackhuang.hmcl.util.Pair.pair;

/// Utilities for reading, installing, and applying modpack-specific game settings.
@NotNullByDefault
public final class ModpackHelper {
    private ModpackHelper() {
    }

    private static final Map<String, ModpackProvider> providers = mapOf(
            pair(CurseModpackProvider.INSTANCE.getName(), CurseModpackProvider.INSTANCE),
            pair(McbbsModpackProvider.INSTANCE.getName(), McbbsModpackProvider.INSTANCE),
            pair(ModrinthModpackProvider.INSTANCE.getName(), ModrinthModpackProvider.INSTANCE),
            pair(MultiMCModpackProvider.INSTANCE.getName(), MultiMCModpackProvider.INSTANCE),
            pair(ServerModpackProvider.INSTANCE.getName(), ServerModpackProvider.INSTANCE),
            pair(HMCLModpackProvider.INSTANCE.getName(), HMCLModpackProvider.INSTANCE)
    );

    static {
        MultiMCComponents.setImplementation(Metadata.FULL_TITLE);
    }

    @Nullable
    public static ModpackProvider getProviderByType(String type) {
        return providers.get(type);
    }

    public static boolean isFileModpackByExtension(Path file) {
        String ext = FileUtils.getExtension(file);
        return "zip".equals(ext) || "mrpack".equals(ext);
    }

    public static Modpack readModpackManifest(Path file, Charset charset) throws UnsupportedModpackException, ManuallyCreatedModpackException {
        try (ZipArchiveReader zipFile = CompressingUtils.openZipFile(file, charset)) {
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

    /// 存储解析启动器包装 ZIP 后的结果
    /// @param innerPath 包装文件系统内的整合包条目路径
    /// @param wrapperFs 包装文件系统；当不再需要 [innerPath] 时必须被关闭
    public record LauncherWrapper(Path innerPath, FileSystem wrapperFs) implements Closeable {
        /// 关闭包装的 [FileSystem]。
        @Override
        public void close() throws IOException {
            wrapperFs.close();
        }
    }

    /// 检测 [file] 是否为 HMCL 启动器包装 ZIP（其内部嵌入了实际的整合包 `modpack.zip` 或 `modpack.mrpack`）
    /// 返回一个包含内部条目路径和包装文件系统的 [LauncherWrapper]，
    /// 如果 [file] 不是包装 ZIP，则返回 `null`
    @Nullable
    public static LauncherWrapper unwrapIfLauncherWrapper(Path file, Charset charset) {
        FileSystem outerFs = null;
        try {
            outerFs = CompressingUtils.createReadOnlyZipFileSystem(file, charset);
            for (String innerName : new String[]{"modpack.zip", "modpack.mrpack"}) {
                Path entryPath = outerFs.getPath("/" + innerName);
                if (Files.isRegularFile(entryPath)) {
                    LauncherWrapper result = new LauncherWrapper(entryPath, outerFs);
                    outerFs = null;
                    return result;
                }
            }
        } catch (IOException ignored) {
        } finally {
            IOUtils.closeQuietly(outerFs);
        }
        return null;
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

    public static ModpackConfiguration<?> readModpackConfiguration(Path file) throws IOException {
        try {
            return JsonUtils.fromJsonFile(file, ModpackConfiguration.class);
        } catch (JsonParseException e) {
            throw new IOException("Malformed modpack configuration");
        }
    }

    public static Task<?> getInstallTask(HMCLGameRepository repository, ServerModpackManifest manifest, String name, Modpack modpack) {
        repository.markVersionAsModpack(name);

        ExceptionalRunnable<?> success = () -> {
            repository.refreshVersions();
            GameSettings.Instance setting = repository.getInstanceGameSettingsOrCreate(name);
            repository.undoMark(name);
            if (setting != null) {
                setting.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
            }
        };

        ExceptionalConsumer<Exception, ?> failure = ex -> {
            if (ex instanceof ModpackCompletionException && !(ex.getCause() instanceof FileNotFoundException)) {
                success.run();
                // This is tolerable and we will not delete the game
            }
        };

        return new ServerModpackRemoteInstallTask(repository.getDependency(), manifest, name)
                .whenComplete(Schedulers.defaultScheduler(), success, failure)
                .withStagesHints(new Task.StagesHint("hmcl.modpack"), new Task.StagesHint("hmcl.modpack.download", List.of("hmcl.install.assets", "hmcl.install.libraries")));
    }

    public static boolean isExternalGameNameConflicts(String name) {
        return Files.exists(Paths.get("externalgames").resolve(name));
    }

    public static Task<?> getInstallManuallyCreatedModpackTask(Path zipFile, String name, Charset charset) {
        if (isExternalGameNameConflicts(name)) {
            throw new IllegalArgumentException("name existing");
        }

        return new ManuallyCreatedModpackInstallTask(zipFile, charset, name)
                .thenAcceptAsync(Schedulers.javafx(), location -> {
                    GameDirectory newGameDirectory = new GameDirectory(
                            GameDirectoryManager.newGameDirectoryId(),
                            LocalizedText.plain(name),
                            PortablePath.fromPath(location));
                    GameDirectoryManager.addLocalGameDirectory(newGameDirectory);
                    GameDirectoryManager.setSelectedGameDirectory(newGameDirectory);
                });
    }

    public static Task<?> getInstallTask(HMCLGameRepository repository, Path zipFile, String name, Modpack modpack, String iconUrl) {
        repository.markVersionAsModpack(name);

        ExceptionalRunnable<?> success = () -> {
            repository.refreshVersions();
            GameSettings.Instance setting = repository.getInstanceGameSettingsOrCreate(name);
            repository.undoMark(name);
            if (setting != null) {
                setting.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
            }
        };

        ExceptionalConsumer<Exception, ?> failure = ex -> {
            if (ex instanceof ModpackCompletionException && !(ex.getCause() instanceof FileNotFoundException)) {
                success.run();
                // This is tolerable and we will not delete the game
            }
        };

        if (modpack.getManifest() instanceof MultiMCInstanceConfiguration)
            return modpack.getInstallTask(repository.getDependency(), zipFile, name, iconUrl)
                    .whenComplete(Schedulers.defaultScheduler(), success, failure)
                    .thenComposeAsync(createMultiMCPostInstallTask(repository, (MultiMCInstanceConfiguration) modpack.getManifest(), name))
                    .withStagesHints(new Task.StagesHint("hmcl.modpack"), new Task.StagesHint("hmcl.modpack.download", List.of("hmcl.install.assets", "hmcl.install.libraries")));
        else if (modpack.getManifest() instanceof McbbsModpackManifest)
            return modpack.getInstallTask(repository.getDependency(), zipFile, name, iconUrl)
                    .whenComplete(Schedulers.defaultScheduler(), success, failure)
                    .thenComposeAsync(createMcbbsPostInstallTask(repository, (McbbsModpackManifest) modpack.getManifest(), name))
                    .withStagesHints(new Task.StagesHint("hmcl.modpack"), new Task.StagesHint("hmcl.modpack.download", List.of("hmcl.install.assets", "hmcl.install.libraries")));
        else
            return modpack.getInstallTask(repository.getDependency(), zipFile, name, iconUrl)
                    .whenComplete(Schedulers.defaultScheduler(), success, failure)
                    .withStagesHints(new Task.StagesHint("hmcl.modpack"), new Task.StagesHint("hmcl.modpack.download", List.of("hmcl.install.assets", "hmcl.install.libraries")));
    }

    public static Task<Void> getUpdateTask(HMCLGameRepository repository, ServerModpackManifest manifest, Charset charset, String name, ModpackConfiguration<?> configuration) throws UnsupportedModpackException {
        switch (configuration.getType()) {
            case ServerModpackRemoteInstallTask.MODPACK_TYPE:
                return new ModpackUpdateTask(repository, name, new ServerModpackRemoteInstallTask(repository.getDependency(), manifest, name))
                        .thenComposeAsync(repository.refreshVersionsAsync())
                        .withStagesHints(new Task.StagesHint("hmcl.modpack"), new Task.StagesHint("hmcl.modpack.download", List.of("hmcl.install.assets", "hmcl.install.libraries")));
            default:
                throw new UnsupportedModpackException();
        }
    }

    public static Task<?> getUpdateTask(HMCLGameRepository repository, Path zipFile, Charset charset, String name, ModpackConfiguration<?> configuration) throws UnsupportedModpackException, ManuallyCreatedModpackException, MismatchedModpackTypeException {
        Modpack modpack = ModpackHelper.readModpackManifest(zipFile, charset);
        ModpackProvider provider = getProviderByType(configuration.getType());
        if (provider == null) {
            throw new UnsupportedModpackException();
        }
        if (modpack.getManifest() instanceof MultiMCInstanceConfiguration)
            return provider.createUpdateTask(repository.getDependency(), name, zipFile, modpack)
                    .thenComposeAsync(() -> createMultiMCPostUpdateTask(repository, (MultiMCInstanceConfiguration) modpack.getManifest(), name))
                    .thenComposeAsync(repository.refreshVersionsAsync());
        else
            return provider.createUpdateTask(repository.getDependency(), name, zipFile, modpack)
                    .thenComposeAsync(repository.refreshVersionsAsync());
    }

    public static void toGameSettings(MultiMCInstanceConfiguration c, GameSettings.Instance setting) {
        setting.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);

        if (c.isOverrideJavaLocation()) {
            setting.getOverrideProperties().add(GameSettings.PROPERTY_JAVA_TYPE);
            setting.getOverrideProperties().add(GameSettings.PROPERTY_CUSTOM_JAVA_PATH);
            setting.javaTypeProperty().setValue(JavaVersionType.CUSTOM);
            setting.customJavaPathProperty().setValue(Objects.requireNonNullElse(c.getJavaPath(), ""));
        }

        if (c.isOverrideMemory()) {
            setting.getOverrideProperties().addAll(List.of(
                    GameSettings.PROPERTY_AUTO_MEMORY,
                    GameSettings.PROPERTY_PERM_SIZE,
                    GameSettings.PROPERTY_MAX_MEMORY,
                    GameSettings.PROPERTY_MIN_MEMORY
            ));
            setting.permSizeProperty().setValue(Optional.ofNullable(c.getPermGen()).map(Object::toString).orElse(""));
            if (c.getMaxMemory() != null)
                setting.maxMemoryProperty().setValue(c.getMaxMemory());
            setting.minMemoryProperty().setValue(c.getMinMemory());
        }

        if (c.isOverrideCommands()) {
            setting.getOverrideProperties().addAll(List.of(
                    GameSettings.PROPERTY_COMMAND_WRAPPER,
                    GameSettings.PROPERTY_PRE_LAUNCH_COMMAND
            ));
            setting.commandWrapperProperty().setValue(Objects.requireNonNullElse(c.getWrapperCommand(), ""));
            setting.preLaunchCommandProperty().setValue(Objects.requireNonNullElse(c.getPreLaunchCommand(), ""));
        }

        if (c.isOverrideJavaArgs()) {
            setting.getOverrideProperties().add(GameSettings.PROPERTY_JVM_OPTIONS);
            setting.jvmOptionsProperty().setValue(Objects.requireNonNullElse(c.getJvmArgs(), ""));
        }

        if (c.isOverrideConsole()) {
            setting.getOverrideProperties().add(GameSettings.PROPERTY_SHOW_LOGS);
            setting.showLogsProperty().setValue(c.isShowConsole());
        }

        if (c.isOverrideWindow()) {
            setting.getOverrideProperties().addAll(List.of(
                    GameSettings.PROPERTY_WINDOW_TYPE,
                    GameSettings.PROPERTY_WIDTH,
                    GameSettings.PROPERTY_HEIGHT
            ));
            setting.windowTypeProperty().setValue(c.isFullscreen() ? GameWindowType.FULLSCREEN : GameWindowType.WINDOWED);
            if (c.getWidth() != null)
                setting.widthProperty().setValue(c.getWidth().doubleValue());
            if (c.getHeight() != null)
                setting.heightProperty().setValue(c.getHeight().doubleValue());
        }
    }

    private static void applyCommandAndJvmSettings(MultiMCInstanceConfiguration c, GameSettings.Instance setting) {
        if (c.isOverrideCommands()) {
            setting.getOverrideProperties().addAll(List.of(
                    GameSettings.PROPERTY_COMMAND_WRAPPER,
                    GameSettings.PROPERTY_PRE_LAUNCH_COMMAND
            ));
            setting.commandWrapperProperty().setValue(Lang.nonNull(c.getWrapperCommand(), ""));
            setting.preLaunchCommandProperty().setValue(Lang.nonNull(c.getPreLaunchCommand(), ""));
        }

        if (c.isOverrideJavaArgs()) {
            setting.getOverrideProperties().add(GameSettings.PROPERTY_JVM_OPTIONS);
            setting.jvmOptionsProperty().setValue(Lang.nonNull(c.getJvmArgs(), ""));
        }
    }

    private static Task<Void> createMultiMCPostUpdateTask(HMCLGameRepository repository, MultiMCInstanceConfiguration manifest, String version) {
        return Task.runAsync(Schedulers.javafx(), () -> {
            GameSettings.Instance setting = Objects.requireNonNull(repository.getInstanceGameSettingsOrCreate(version));
            ModpackHelper.applyCommandAndJvmSettings(manifest, setting);
        });
    }

    private static Task<Void> createMultiMCPostInstallTask(HMCLGameRepository repository, MultiMCInstanceConfiguration manifest, String version) {
        return Task.runAsync(Schedulers.javafx(), () -> {
            GameSettings.Instance setting = Objects.requireNonNull(repository.getInstanceGameSettingsOrCreate(version));
            ModpackHelper.toGameSettings(manifest, setting);
        });
    }

    private static Task<Void> createMcbbsPostInstallTask(HMCLGameRepository repository, McbbsModpackManifest manifest, String version) {
        return Task.runAsync(Schedulers.javafx(), () -> {
            GameSettings.Effective effective = repository.getEffectiveGameSettings(version);
            if (manifest.getLaunchInfo().getMinMemory() > effective.getMaxMemory()) {
                GameSettings.Instance setting = Objects.requireNonNull(repository.getInstanceGameSettingsOrCreate(version));
                setting.getOverrideProperties().addAll(List.of(
                        GameSettings.PROPERTY_AUTO_MEMORY,
                        GameSettings.PROPERTY_MIN_MEMORY,
                        GameSettings.PROPERTY_MAX_MEMORY,
                        GameSettings.PROPERTY_PERM_SIZE
                ));
                setting.autoMemoryProperty().setValue(effective.getInheritable(GameSettings::autoMemoryProperty));
                setting.minMemoryProperty().setValue(effective.getInheritable(GameSettings::minMemoryProperty));
                setting.maxMemoryProperty().setValue(manifest.getLaunchInfo().getMinMemory());
                setting.permSizeProperty().setValue(effective.getInheritable(GameSettings::permSizeProperty));
            }
        });
    }
}
