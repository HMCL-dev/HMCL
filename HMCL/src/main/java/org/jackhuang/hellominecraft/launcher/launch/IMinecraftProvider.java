/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui
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
package org.jackhuang.hellominecraft.launcher.launch;

import java.io.File;
import java.util.Collection;
import org.jackhuang.hellominecraft.launcher.utils.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.settings.Profile;
import org.jackhuang.hellominecraft.launcher.version.MinecraftVersion;
import org.jackhuang.hellominecraft.utils.StrUtils;

/**
 * Provide everything of the Minecraft of a Profile.
 *
 * @see org.jackhuang.hellominecraft.launcher.version.MinecraftVersionManager
 * @author huangyuhui
 */
public abstract class IMinecraftProvider {

    protected Profile profile;

    public IMinecraftProvider(Profile profile) {
        this.profile = profile;
    }

    /**
     * To download mod packs.
     */
    public abstract void initializeMiencraft();

    /**
     * Get the run directory of given version.
     *
     * @param id the given version name
     *
     * @return the run directory
     */
    public abstract File getRunDirectory(String id);

    public File getRunDirectory(String id, String subFolder) {
        return new File(getRunDirectory(getSelectedVersion().id), subFolder);
    }

    public abstract void open(String version, String folder);

    public abstract IMinecraftModService getModService();

    public abstract IMinecraftDownloadService getDownloadService();

    public abstract IMinecraftAssetService getAssetService();

    /**
     * Returns the thing like ".minecraft/resourcepacks".
     *
     * @return the thing
     */
    public abstract File getResourcePacks();

    /**
     *
     * @param v should be resolved
     *
     * @return libraries of resolved minecraft version v.
     */
    public abstract GameLauncher.DecompressLibraryJob getDecompressLibraries(MinecraftVersion v) throws GameException;

    public abstract File getDecompressNativesToLocation(MinecraftVersion v);

    /**
     * @return the Minecraft jar of selected version.
     */
    public abstract File getMinecraftJar();

    /**
     * @return the base folder of the profile.
     */
    public abstract File getBaseFolder();

    /**
     * Provide the Minecraft Loader to generate the launching command.
     *
     * @see org.jackhuang.hellominecraft.launcher.launch.IMinecraftLoader
     * @param p player informations, including username & auth_token
     *
     * @return what you want
     *
     * @throws GameException circular denpendency versions
     */
    public abstract IMinecraftLoader provideMinecraftLoader(UserProfileProvider p) throws GameException;

    /**
     * Rename version
     *
     * @param from The old name
     * @param to   The new name
     *
     * @return Is the action successful?
     */
    public abstract boolean renameVersion(String from, String to);

    /**
     * Remove the given version from disk.
     *
     * @param a the version name
     *
     * @return Is the action successful?
     */
    public abstract boolean removeVersionFromDisk(String a);

    /**
     * Redownload the Minecraft json of the given version.
     *
     * @param id the given version name
     *
     * @return Is the action successful?
     */
    public boolean refreshJson(String id) {
        return getDownloadService().downloadMinecraftVersionJson(id);
    }

    /**
     * Choose a version randomly.
     *
     * @return the version
     */
    public abstract MinecraftVersion getOneVersion();

    /**
     * All Minecraft version in this profile.
     *
     * @return the collection of all Minecraft version
     */
    public abstract Collection<MinecraftVersion> getVersions();

    /**
     * Get the Minecraft json instance of given version.
     *
     * @param id the given version name
     *
     * @return the Minecraft json instance
     */
    public abstract MinecraftVersion getVersionById(String id);

    public MinecraftVersion getSelectedVersion() {
        String versionName = profile.getSelectedMinecraftVersionName();
        MinecraftVersion v = null;
        if (StrUtils.isNotBlank(versionName))
            v = getVersionById(versionName);
        if (v == null)
            v = getOneVersion();
        if (v != null)
            profile.setSelectedMinecraftVersion(v.id);
        return v;
    }

    /**
     * getVersions().size()
     *
     * @return getVersions().size()
     */
    public abstract int getVersionCount();

    /**
     * Refind the versions in this profile.
     */
    public abstract void refreshVersions();

    /**
     * Clean redundant files.
     */
    public abstract void cleanFolder();

    /**
     * When GameLauncher launches the game, this function will be called.
     *
     * @return if false, will break the launch process.
     */
    public abstract boolean onLaunch();

}
