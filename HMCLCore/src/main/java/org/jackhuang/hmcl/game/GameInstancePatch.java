/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.JsonElement;
import org.jackhuang.hmcl.util.Lang;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@NotNullByDefault
public record GameInstancePatch(
        @Nullable String id,
        @Nullable String version,
        Integer priority,
        @Nullable String minecraftArguments,
        @Nullable Arguments arguments,
        @Nullable String mainClass,
        @Nullable String inheritsFrom,
        @Nullable GameInstanceID jar,
        @Nullable AssetIndexInfo assetIndex,
        @Nullable String assets,
        @Nullable Integer complianceLevel,
        @Nullable GameJavaVersion javaVersion,
        @Nullable @Unmodifiable List<Library> libraries,
        @Nullable @Unmodifiable List<CompatibilityRule> compatibilityRules,
        @Nullable @Unmodifiable Map<DownloadType, DownloadInfo> downloads,
        @Nullable @Unmodifiable Map<DownloadType, LoggingInfo> logging,
        @Nullable ReleaseType type,
        @Nullable String time,
        @Nullable String releaseTime,
        @Nullable Integer minimumLauncherVersion,
        @Nullable Boolean hidden,
        @Unmodifiable Map<String, JsonElement> unknownFields
) {

    /// Returns a patch copy with the given jar target.
    ///
    /// @param jar the jar target, or `null` to let the parent manifest supply it
    /// @return a patch with the requested jar target
    public GameInstancePatch withJar(@Nullable GameInstanceID jar) {
        if (Objects.equals(this.jar, jar)) {
            return this;
        }

        return new GameInstancePatch(
                id,
                version,
                priority,
                minecraftArguments,
                arguments,
                mainClass,
                inheritsFrom,
                jar,
                assetIndex,
                assets,
                complianceLevel,
                javaVersion,
                libraries,
                compatibilityRules,
                downloads,
                logging,
                type,
                time,
                releaseTime,
                minimumLauncherVersion,
                hidden,
                unknownFields);
    }

    GameInstanceManifest merge(GameInstanceManifest parent) {
        return new GameInstanceManifest(
                parent.id(),
                minecraftArguments == null ? parent.minecraftArguments() : minecraftArguments,
                Arguments.merge(parent.arguments(), arguments),
                mainClass == null ? parent.mainClass() : mainClass,
                null, // inheritsFrom
                jar == null ? parent.jar() : jar,
                assetIndex == null ? parent.assetIndex() : assetIndex,
                assets == null ? parent.assets() : assets,
                complianceLevel,
                javaVersion == null ? parent.javaVersion() : javaVersion,
                Lang.merge(this.libraries, parent.libraries()),
                Lang.merge(parent.compatibilityRules(), this.compatibilityRules),
                downloads == null ? parent.downloads() : downloads,
                logging == null ? parent.logging() : logging,
                type == null ? parent.type() : type,
                time == null ? parent.time() : time,
                releaseTime == null ? parent.releaseTime() : releaseTime,
                Lang.merge(minimumLauncherVersion, parent.minimumLauncherVersion(), Math::max),
                hidden,
                true,
                parent.patches(),
                null);
    }
}
