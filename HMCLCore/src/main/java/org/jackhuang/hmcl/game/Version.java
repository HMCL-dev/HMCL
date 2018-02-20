/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.*;

import java.util.*;
import java.util.logging.Level;

/**
 *
 * @author huangyuhui
 */
@Immutable
public class Version implements Comparable<Version>, Validation {

    private final String id;
    private final String minecraftArguments;
    private final Arguments arguments;
    private final String mainClass;
    private final String inheritsFrom;
    private final String jar;
    private final AssetIndexInfo assetIndex;
    private final String assets;
    private final List<Library> libraries;
    private final List<CompatibilityRule> compatibilityRules;
    private final Map<DownloadType, DownloadInfo> downloads;
    private final Map<DownloadType, LoggingInfo> logging;
    private final ReleaseType type;
    private final Date time;
    private final Date releaseTime;
    private final int minimumLauncherVersion;

    public Version(String id, String minecraftArguments, Arguments arguments, String mainClass, String inheritsFrom, String jar, AssetIndexInfo assetIndex, String assets, List<Library> libraries, List<CompatibilityRule> compatibilityRules, Map<DownloadType, DownloadInfo> downloads, Map<DownloadType, LoggingInfo> logging, ReleaseType type, Date time, Date releaseTime, int minimumLauncherVersion) {
        this.id = id;
        this.minecraftArguments = minecraftArguments;
        this.arguments = arguments;
        this.mainClass = mainClass;
        this.inheritsFrom = inheritsFrom;
        this.jar = jar;
        this.assetIndex = assetIndex;
        this.assets = assets;
        this.libraries = new LinkedList<>(libraries);
        this.compatibilityRules = compatibilityRules == null ? null : new LinkedList<>(compatibilityRules);
        this.downloads = downloads == null ? null : new HashMap<>(downloads);
        this.logging = logging == null ? null : new HashMap<>(logging);
        this.type = type;
        this.time = time == null ? new Date() : (Date) time.clone();
        this.releaseTime = releaseTime == null ? new Date() : (Date) releaseTime.clone();
        this.minimumLauncherVersion = minimumLauncherVersion;
    }

    public Optional<String> getMinecraftArguments() {
        return Optional.ofNullable(minecraftArguments);
    }

    public Optional<Arguments> getArguments() {
        return Optional.ofNullable(arguments);
    }

    public String getMainClass() {
        return mainClass;
    }

    public Date getTime() {
        return time;
    }

    public String getId() {
        return id;
    }

    public ReleaseType getType() {
        return type;
    }

    public Date getReleaseTime() {
        return releaseTime;
    }

    public String getJar() {
        return jar;
    }

    public String getInheritsFrom() {
        return inheritsFrom;
    }

    public int getMinimumLauncherVersion() {
        return minimumLauncherVersion;
    }

    public Map<DownloadType, LoggingInfo> getLogging() {
        return logging == null ? Collections.EMPTY_MAP : Collections.unmodifiableMap(logging);
    }

    public List<Library> getLibraries() {
        return libraries == null ? Collections.EMPTY_LIST : Collections.unmodifiableList(libraries);
    }

    public List<CompatibilityRule> getCompatibilityRules() {
        return compatibilityRules == null ? Collections.EMPTY_LIST : Collections.unmodifiableList(compatibilityRules);
    }

    public DownloadInfo getDownloadInfo() {
        DownloadInfo client = downloads == null ? null : downloads.get(DownloadType.CLIENT);
        String jarName = jar == null ? id : jar;
        if (client == null)
            return new DownloadInfo(String.format("%s%s/%s.jar", Constants.DEFAULT_VERSION_DOWNLOAD_URL, jarName, jarName));
        else
            return client;
    }

    public AssetIndexInfo getAssetIndex() {
        String assetsId = assets == null ? "legacy" : assets;
        return assetIndex == null ? new AssetIndexInfo(assetsId, Constants.DEFAULT_INDEX_URL + assetsId + ".json") : assetIndex;
    }

    public boolean appliesToCurrentEnvironment() {
        return CompatibilityRule.appliesToCurrentEnvironment(compatibilityRules);
    }

    public boolean install(String id) {
        return false;
    }

    /**
     * Resolve given version
     */
    public Version resolve(VersionProvider provider) throws VersionNotFoundException {
        return resolve(provider, new HashSet<>());
    }

    protected Version resolve(VersionProvider provider, Set<String> resolvedSoFar) throws VersionNotFoundException {
        if (inheritsFrom == null)
            return this;

        // To maximize the compatibility.
        if (!resolvedSoFar.add(id)) {
            Logging.LOG.log(Level.WARNING, "Found circular dependency versions: {0}", resolvedSoFar);
            return this;
        }

        // It is supposed to auto install an version in getVersion.
        Version parent = provider.getVersion(inheritsFrom).resolve(provider, resolvedSoFar);
        return new Version(id,
                minecraftArguments == null ? parent.minecraftArguments : minecraftArguments,
                Arguments.merge(parent.arguments, arguments),
                mainClass == null ? parent.mainClass : mainClass,
                null, // inheritsFrom
                jar == null ? parent.jar : jar,
                assetIndex == null ? parent.assetIndex : assetIndex,
                assets == null ? parent.assets : assets,
                Lang.merge(parent.libraries, this.libraries),
                Lang.merge(parent.compatibilityRules, this.compatibilityRules),
                downloads == null ? parent.downloads : downloads,
                logging == null ? parent.logging : logging,
                type,
                time,
                releaseTime,
                Math.max(minimumLauncherVersion, parent.minimumLauncherVersion));
    }

    public Version setId(String id) {
        return new Version(id, minecraftArguments, arguments, mainClass, inheritsFrom, jar, assetIndex, assets, libraries, compatibilityRules, downloads, logging, type, time, releaseTime, minimumLauncherVersion);
    }

    public Version setMinecraftArguments(String minecraftArguments) {
        return new Version(id, minecraftArguments, arguments, mainClass, inheritsFrom, jar, assetIndex, assets, libraries, compatibilityRules, downloads, logging, type, time, releaseTime, minimumLauncherVersion);
    }

    public Version setArguments(Arguments arguments) {
        return new Version(id, minecraftArguments, arguments, mainClass, inheritsFrom, jar, assetIndex, assets, libraries, compatibilityRules, downloads, logging, type, time, releaseTime, minimumLauncherVersion);
    }

    public Version setMainClass(String mainClass) {
        return new Version(id, minecraftArguments, arguments, mainClass, inheritsFrom, jar, assetIndex, assets, libraries, compatibilityRules, downloads, logging, type, time, releaseTime, minimumLauncherVersion);
    }

    public Version setInheritsFrom(String inheritsFrom) {
        return new Version(id, minecraftArguments, arguments, mainClass, inheritsFrom, jar, assetIndex, assets, libraries, compatibilityRules, downloads, logging, type, time, releaseTime, minimumLauncherVersion);
    }

    public Version setJar(String jar) {
        return new Version(id, minecraftArguments, arguments, mainClass, inheritsFrom, jar, assetIndex, assets, libraries, compatibilityRules, downloads, logging, type, time, releaseTime, minimumLauncherVersion);
    }

    public Version setLibraries(List<Library> libraries) {
        return new Version(id, minecraftArguments, arguments, mainClass, inheritsFrom, jar, assetIndex, assets, libraries, compatibilityRules, downloads, logging, type, time, releaseTime, minimumLauncherVersion);
    }

    public Version setLogging(Map<DownloadType, LoggingInfo> logging) {
        return new Version(id, minecraftArguments, arguments, mainClass, inheritsFrom, jar, assetIndex, assets, libraries, compatibilityRules, downloads, logging, type, time, releaseTime, minimumLauncherVersion);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Version && Objects.equals(id, ((Version) obj).id);
    }

    @Override
    public int compareTo(Version o) {
        return id.compareTo(o.id);
    }

    @Override
    public void validate() throws JsonParseException {
        if (StringUtils.isBlank(id))
            throw new JsonParseException("Version ID cannot be blank");
        if (downloads != null)
            for (Map.Entry entry : downloads.entrySet()) {
                if (!(entry.getKey() instanceof DownloadType))
                    throw new JsonParseException("Version downloads key must be DownloadType");
                if (!(entry.getValue() instanceof DownloadInfo))
                    throw new JsonParseException("Version downloads value must be DownloadInfo");
            }
        if (logging != null)
            for (Map.Entry entry : logging.entrySet()) {
                if (!(entry.getKey() instanceof DownloadType))
                    throw new JsonParseException("Version logging key must be DownloadType");
                if (!(entry.getValue() instanceof LoggingInfo))
                    throw new JsonParseException("Version logging value must be LoggingInfo");
            }
    }

}
