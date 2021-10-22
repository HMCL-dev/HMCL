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
package org.jackhuang.hmcl.game.tlauncher;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.util.gson.JsonMap;
import org.jackhuang.hmcl.util.gson.TolerableValidationException;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class TLauncherVersion implements Validation {

    private final String id;
    private final String minecraftArguments;
    private final Arguments arguments;
    private final String mainClass;
    private final String inheritsFrom;
    private final String jar;
    private final AssetIndexInfo assetIndex;
    private final String assets;
    private final Integer complianceLevel;
    @Nullable
    private final GameJavaVersion javaVersion;
    private final List<TLauncherLibrary> libraries;
    private final List<CompatibilityRule> compatibilityRules;
    private final JsonMap<DownloadType, DownloadInfo> downloads;
    private final JsonMap<DownloadType, LoggingInfo> logging;
    private final ReleaseType type;
    private final Date time;
    private final Date releaseTime;
    private final Integer minimumLauncherVersion;
    private final Integer tlauncherVersion;

    public TLauncherVersion(String id, String minecraftArguments, Arguments arguments, String mainClass, String inheritsFrom, String jar, AssetIndexInfo assetIndex, String assets, Integer complianceLevel, @Nullable GameJavaVersion javaVersion, List<TLauncherLibrary> libraries, List<CompatibilityRule> compatibilityRules, JsonMap<DownloadType, DownloadInfo> downloads, JsonMap<DownloadType, LoggingInfo> logging, ReleaseType type, Date time, Date releaseTime, Integer minimumLauncherVersion, Integer tlauncherVersion) {
        this.id = id;
        this.minecraftArguments = minecraftArguments;
        this.arguments = arguments;
        this.mainClass = mainClass;
        this.inheritsFrom = inheritsFrom;
        this.jar = jar;
        this.assetIndex = assetIndex;
        this.assets = assets;
        this.complianceLevel = complianceLevel;
        this.javaVersion = javaVersion;
        this.libraries = libraries;
        this.compatibilityRules = compatibilityRules;
        this.downloads = downloads;
        this.logging = logging;
        this.type = type;
        this.time = time;
        this.releaseTime = releaseTime;
        this.minimumLauncherVersion = minimumLauncherVersion;
        this.tlauncherVersion = tlauncherVersion;
    }

    @Override
    public void validate() throws JsonParseException, TolerableValidationException {
        Validation.requireNonNull(tlauncherVersion, "Not TLauncher version json format");
    }

    public Version toVersion() {
        return new Version(
                false,
                id,
                null,
                null,
                minecraftArguments,
                arguments,
                mainClass,
                inheritsFrom,
                jar,
                assetIndex,
                assets,
                complianceLevel,
                javaVersion,
                libraries == null ? null : libraries.stream().map(TLauncherLibrary::toLibrary).collect(Collectors.toList()),
                compatibilityRules,
                downloads,
                logging,
                type,
                time,
                releaseTime,
                minimumLauncherVersion,
                null,
                null,
                null
        );
    }
}
