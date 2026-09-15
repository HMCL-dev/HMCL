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
package org.jackhuang.hmcl.util.i18n;

import org.jackhuang.hmcl.addon.AddonLoader;
import org.jackhuang.hmcl.addon.AddonLoaderType;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.download.ComponentRemoteVersion;
import org.jackhuang.hmcl.download.cleanroom.CleanroomInstallTask;
import org.jackhuang.hmcl.download.fabric.FabricAPIInstallTask;
import org.jackhuang.hmcl.download.fabric.FabricInstallTask;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallTask;
import org.jackhuang.hmcl.download.forge.ForgeOldInstallTask;
import org.jackhuang.hmcl.download.game.GameAssetDownloadTask;
import org.jackhuang.hmcl.download.game.GameInstallTask;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.download.java.mojang.MojangJavaDownloadTask;
import org.jackhuang.hmcl.download.legacyfabric.LegacyFabricInstallTask;
import org.jackhuang.hmcl.download.liteloader.LiteLoaderInstallTask;
import org.jackhuang.hmcl.download.neoforge.NeoForgeInstallTask;
import org.jackhuang.hmcl.download.neoforge.NeoForgeOldInstallTask;
import org.jackhuang.hmcl.download.optifine.OptiFineInstallTask;
import org.jackhuang.hmcl.download.quilt.QuiltAPIInstallTask;
import org.jackhuang.hmcl.download.quilt.QuiltInstallTask;
import org.jackhuang.hmcl.game.HMCLModpackInstallTask;
import org.jackhuang.hmcl.java.JavaInstallTask;
import org.jackhuang.hmcl.modpack.MinecraftInstanceTask;
import org.jackhuang.hmcl.modpack.ModpackInstallTask;
import org.jackhuang.hmcl.modpack.ModpackUpdateTask;
import org.jackhuang.hmcl.modpack.curse.CurseCompletionTask;
import org.jackhuang.hmcl.modpack.curse.CurseInstallTask;
import org.jackhuang.hmcl.modpack.mcbbs.McbbsModpackCompletionTask;
import org.jackhuang.hmcl.modpack.mcbbs.McbbsModpackExportTask;
import org.jackhuang.hmcl.modpack.modrinth.ModrinthCompletionTask;
import org.jackhuang.hmcl.modpack.modrinth.ModrinthInstallTask;
import org.jackhuang.hmcl.modpack.modrinth.ModrinthModpackExportTask;
import org.jackhuang.hmcl.modpack.multimc.MultiMCModpackInstallTask;
import org.jackhuang.hmcl.modpack.server.ServerModpackCompletionTask;
import org.jackhuang.hmcl.modpack.server.ServerModpackExportTask;
import org.jackhuang.hmcl.modpack.server.ServerModpackLocalInstallTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.translator.Translator;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.temporal.TemporalAccessor;
import java.util.*;

public final class I18n {

    private I18n() {
    }

    private static volatile SupportedLocale locale = SupportedLocale.DEFAULT;

    public static void setLocale(SupportedLocale locale) {
        I18n.locale = locale;
    }

    public static SupportedLocale getLocale() {
        return locale;
    }

    public static boolean isUpsideDown() {
        return LocaleUtils.getScript(locale.getDisplayLocale()).equals("Qabs");
    }

    public static boolean isUseChinese() {
        return LocaleUtils.isChinese(locale.getDisplayLocale());
    }

    public static ResourceBundle getResourceBundle() {
        return locale.getResourceBundle();
    }

    public static Translator getTranslator() {
        return locale.getTranslator();
    }

    public static String i18n(@PropertyKey(resourceBundle = "assets.lang.I18N") String key, Object... formatArgs) {
        return locale.i18n(key, formatArgs);
    }

    public static String i18n(@PropertyKey(resourceBundle = "assets.lang.I18N") String key) {
        return locale.i18n(key);
    }

    public static String formatDateTime(TemporalAccessor time) {
        return getTranslator().formatDateTime(time);
    }

    public static String formatSpeed(long bytes) {
        return getTranslator().formatSpeed(bytes);
    }

    public static String getDisplayVersion(ComponentRemoteVersion version) {
        return getTranslator().getDisplayVersion(version);
    }

    public static String getDisplayVersion(GameVersionNumber version) {
        return getTranslator().getDisplayVersion(version);
    }

    /// Find the builtin localized resource with given name and suffix.
    ///
    /// For example, if the current locale is `zh-CN`, when calling `getBuiltinResource("assets.lang.foo", "json")`,
    /// this method will look for the following built-in resources in order:
    ///
    ///  - `assets/lang/foo_zh_Hans_CN.json`
    ///  - `assets/lang/foo_zh_Hans.json`
    ///  - `assets/lang/foo_zh_CN.json`
    ///  - `assets/lang/foo_zh.json`
    ///  - `assets/lang/foo.json`
    ///
    /// This method will return the first found resource;
    /// if none of the above resources exist, it returns `null`.
    public static @Nullable URL getBuiltinResource(String name, String suffix) {
        var control = DefaultResourceBundleControl.INSTANCE;
        var classLoader = I18n.class.getClassLoader();
        for (Locale locale : locale.getCandidateLocales()) {
            String resourceName = control.toResourceName(control.toBundleName(name, locale), suffix);
            URL input = classLoader.getResource(resourceName);
            if (input != null)
                return input;
        }
        return null;
    }

    /// @see [#getBuiltinResource(String, String) ]
    public static @Nullable InputStream getBuiltinResourceAsStream(String name, String suffix) {
        URL resource = getBuiltinResource(name, suffix);
        try {
            return resource != null ? resource.openStream() : null;
        } catch (IOException e) {
            return null;
        }
    }

    public static String getWikiLink(GameRemoteVersion remoteVersion) {
        return MinecraftWiki.getWikiLink(locale, remoteVersion);
    }

    public static String translateLoaderType(AddonLoaderType loaderType) {
        if (loaderType instanceof ModLoaderType modLoaderType) {
            return switch (modLoaderType) {
                case FORGE -> i18n("install.installer.forge");
                case CLEANROOM -> i18n("install.installer.cleanroom");
                case NEO_FORGE -> i18n("install.installer.neoforge");
                case FABRIC -> i18n("install.installer.fabric");
                case LITE_LOADER -> i18n("install.installer.liteloader");
                case QUILT -> i18n("install.installer.quilt");
                case LEGACY_FABRIC -> i18n("install.installer.legacyfabric");
                default -> modLoaderType.displayName();
            };
        }
        return loaderType.displayName();
    }

    public static String translateLoaderName(AddonLoader loader) {
        if (loader.type() != null)
            return translateLoaderType(loader.type());
        else return "bungeecord".equalsIgnoreCase(loader.name())
                ? "BungeeCord"
                : StringUtils.capitalizeWords(loader.name());
    }

    public static @Nullable String translateTaskName(Task<?> task) {
        if (task instanceof GameAssetDownloadTask) {
            task.setName(i18n("assets.download_all"));
        } else if (task instanceof GameInstallTask) {
            if (task.getInheritedStage() != null && task.getInheritedStage().startsWith("hmcl.install.game"))
                return null;
            task.setName(i18n("install.installer.install", i18n("install.installer.game")));
        } else if (task instanceof CleanroomInstallTask) {
            task.setName(i18n("install.installer.install", i18n("install.installer.cleanroom")));
        } else if (task instanceof LegacyFabricInstallTask) {
            task.setName(i18n("install.installer.install", i18n("install.installer.legacyfabric")));
        } else if (task instanceof ForgeNewInstallTask || task instanceof ForgeOldInstallTask) {
            task.setName(i18n("install.installer.install", i18n("install.installer.forge")));
        } else if (task instanceof NeoForgeInstallTask || task instanceof NeoForgeOldInstallTask) {
            task.setName(i18n("install.installer.install", i18n("install.installer.neoforge")));
        } else if (task instanceof LiteLoaderInstallTask) {
            task.setName(i18n("install.installer.install", i18n("install.installer.liteloader")));
        } else if (task instanceof OptiFineInstallTask) {
            task.setName(i18n("install.installer.install", i18n("install.installer.optifine")));
        } else if (task instanceof FabricInstallTask) {
            task.setName(i18n("install.installer.install", i18n("install.installer.fabric")));
        } else if (task instanceof FabricAPIInstallTask) {
            task.setName(i18n("install.installer.install", i18n("install.installer.fabric-api")));
        } else if (task instanceof QuiltInstallTask) {
            task.setName(i18n("install.installer.install", i18n("install.installer.quilt")));
        } else if (task instanceof QuiltAPIInstallTask) {
            task.setName(i18n("install.installer.install", i18n("install.installer.quilt-api")));
        } else if (task instanceof CurseCompletionTask || task instanceof ModrinthCompletionTask || task instanceof ServerModpackCompletionTask || task instanceof McbbsModpackCompletionTask) {
            task.setName(i18n("modpack.completion"));
        } else if (task instanceof ModpackInstallTask) {
            task.setName(i18n("modpack.installing"));
        } else if (task instanceof ModpackUpdateTask) {
            task.setName(i18n("modpack.update"));
        } else if (task instanceof CurseInstallTask) {
            task.setName(i18n("modpack.installing.given", i18n("modpack.type.curse")));
        } else if (task instanceof MultiMCModpackInstallTask) {
            task.setName(i18n("modpack.installing.given", i18n("modpack.type.multimc")));
        } else if (task instanceof ModrinthInstallTask) {
            task.setName(i18n("modpack.installing.given", i18n("modpack.type.modrinth")));
        } else if (task instanceof ServerModpackLocalInstallTask) {
            task.setName(i18n("install.installing") + ": " + i18n("modpack.type.server"));
        } else if (task instanceof HMCLModpackInstallTask) {
            task.setName(i18n("modpack.installing.given", "HMCL"));
        } else if (task instanceof McbbsModpackExportTask || task instanceof ServerModpackExportTask || task instanceof ModrinthModpackExportTask) {
            task.setName(i18n("modpack.export"));
        } else if (task instanceof MinecraftInstanceTask) {
            task.setName(i18n("modpack.scan"));
        } else if (task instanceof MojangJavaDownloadTask) {
            task.setName(i18n("download.java"));
        } else if (task instanceof JavaInstallTask) {
            task.setName(i18n("java.installing"));
        }

        return null;
    }

    public static boolean hasKey(String key) {
        return getResourceBundle().containsKey(key);
    }
}
