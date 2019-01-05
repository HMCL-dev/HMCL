/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.mod.*;
import org.jackhuang.hmcl.setting.EnumGameDirectory;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jackhuang.hmcl.util.function.ExceptionalRunnable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;

public final class ModpackHelper {
    private ModpackHelper() {}

    public static Modpack readModpackManifest(Path file, Charset charset) throws UnsupportedModpackException {
        try {
            return CurseManifest.readCurseForgeModpackManifest(file, charset);
        } catch (Exception e) {
            // ignore it, not a valid CurseForge modpack.
        }

        try {
            return HMCLModpackManager.readHMCLModpackManifest(file, charset);
        } catch (Exception e) {
            // ignore it, not a valid HMCL modpack.
        }

        try {
            return MultiMCInstanceConfiguration.readMultiMCModpackManifest(file, charset);
        } catch (Exception e) {
            // ignore it, not a valid MultiMC modpack.
        }

        throw new UnsupportedModpackException(file.toString());
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

    private static String getManifestType(Object manifest) throws UnsupportedModpackException {
        if (manifest instanceof HMCLModpackManifest)
            return HMCLModpackInstallTask.MODPACK_TYPE;
        else if (manifest instanceof MultiMCInstanceConfiguration)
            return MultiMCModpackInstallTask.MODPACK_TYPE;
        else if (manifest instanceof CurseManifest)
            return CurseInstallTask.MODPACK_TYPE;
        else
            throw new UnsupportedModpackException();
    }

    public static Task getInstallTask(Profile profile, File zipFile, String name, Modpack modpack) {
        profile.getRepository().markVersionAsModpack(name);

        ExceptionalRunnable<?> success = () -> {
            HMCLGameRepository repository = profile.getRepository();
            repository.refreshVersions();
            VersionSetting vs = repository.specializeVersionSetting(name);
            repository.undoMark(name);
            if (vs != null)
                vs.setGameDirType(EnumGameDirectory.VERSION_FOLDER);
        };

        ExceptionalConsumer<Exception, ?> failure = ex -> {
            if (ex instanceof CurseCompletionException && !(ex.getCause() instanceof FileNotFoundException)) {
                success.run();
                // This is tolerable and we will not delete the game
            } else {
                HMCLGameRepository repository = profile.getRepository();
                repository.removeVersionFromDisk(name);
            }
        };

        if (modpack.getManifest() instanceof CurseManifest)
            return new CurseInstallTask(profile.getDependency(), zipFile, modpack, ((CurseManifest) modpack.getManifest()), name)
                    .finalized(Schedulers.defaultScheduler(), ExceptionalConsumer.fromRunnable(success), failure);
        else if (modpack.getManifest() instanceof HMCLModpackManifest)
            return new HMCLModpackInstallTask(profile, zipFile, modpack, name)
                    .finalized(Schedulers.defaultScheduler(), ExceptionalConsumer.fromRunnable(success), failure);
        else if (modpack.getManifest() instanceof MultiMCInstanceConfiguration)
            return new MultiMCModpackInstallTask(profile.getDependency(), zipFile, modpack, ((MultiMCInstanceConfiguration) modpack.getManifest()), name)
                    .finalized(Schedulers.defaultScheduler(), ExceptionalConsumer.fromRunnable(success), failure)
                    .then(new MultiMCInstallVersionSettingTask(profile, ((MultiMCInstanceConfiguration) modpack.getManifest()), name));
        else throw new IllegalStateException("Unrecognized modpack: " + modpack);
    }

    public static Task getUpdateTask(Profile profile, File zipFile, Charset charset, String name, ModpackConfiguration<?> configuration) throws UnsupportedModpackException, MismatchedModpackTypeException {
        Modpack modpack = ModpackHelper.readModpackManifest(zipFile.toPath(), charset);

        switch (configuration.getType()) {
            case CurseInstallTask.MODPACK_TYPE:
                if (!(modpack.getManifest() instanceof CurseManifest))
                    throw new MismatchedModpackTypeException(CurseInstallTask.MODPACK_TYPE, getManifestType(modpack.getManifest()));

                return new ModpackUpdateTask(profile.getRepository(), name, new CurseInstallTask(profile.getDependency(), zipFile, modpack, (CurseManifest) modpack.getManifest(), name));
            case MultiMCModpackInstallTask.MODPACK_TYPE:
                if (!(modpack.getManifest() instanceof MultiMCInstanceConfiguration))
                    throw new MismatchedModpackTypeException(MultiMCModpackInstallTask.MODPACK_TYPE, getManifestType(modpack.getManifest()));

                return new ModpackUpdateTask(profile.getRepository(), name, new MultiMCModpackInstallTask(profile.getDependency(), zipFile, modpack, (MultiMCInstanceConfiguration) modpack.getManifest(), name));
            case HMCLModpackInstallTask.MODPACK_TYPE:
                if (!(modpack.getManifest() instanceof HMCLModpackManifest))
                    throw new MismatchedModpackTypeException(HMCLModpackInstallTask.MODPACK_TYPE, getManifestType(modpack.getManifest()));

                return new ModpackUpdateTask(profile.getRepository(), name, new HMCLModpackInstallTask(profile, zipFile, modpack, name));
            default:
                throw new UnsupportedModpackException();
        }
    }

    public static void toVersionSetting(MultiMCInstanceConfiguration c, VersionSetting vs) {
        vs.setUsesGlobal(false);
        vs.setGameDirType(EnumGameDirectory.VERSION_FOLDER);

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


}
