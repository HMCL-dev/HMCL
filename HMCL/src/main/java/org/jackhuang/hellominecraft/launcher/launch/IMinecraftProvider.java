/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.launcher.launch;

import java.io.File;
import java.util.Collection;
import java.util.List;
import org.jackhuang.hellominecraft.launcher.utils.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;
import org.jackhuang.hellominecraft.launcher.settings.Profile;
import org.jackhuang.hellominecraft.launcher.version.MinecraftVersion;

/**
 * Provide everything of the Minecraft of a Profile.
 * @see org.jackhuang.hellominecraft.launcher.version.MinecraftVersionManager
 * @author huangyuhui
 */
public abstract class IMinecraftProvider {

    Profile profile;

    public IMinecraftProvider(Profile profile) {
        this.profile = profile;
    }

    /**
     * Get the run directory of given version.
     * @param id the given version name
     * @return the run directory
     */
    public abstract File getRunDirectory(String id);

    /**
     * Get the libraries that need to download.
     * @param type where to download
     * @return the library collection
     */
    public abstract List<GameLauncher.DownloadLibraryJob> getDownloadLibraries(DownloadType type);

    public abstract void openSelf(String version);

    public abstract void open(String version, String folder);

    public abstract File getAssets();

    /**
     * Returns the thing like ".minecraft/resourcepacks".
     * @return the thing
     */
    public abstract File getResourcePacks();

    public abstract GameLauncher.DecompressLibraryJob getDecompressLibraries();

    public abstract File getDecompressNativesToLocation();

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
     * @param type where to download
     * @return what you want
     * @throws IllegalStateException circular denpendency versions
     */
    public abstract IMinecraftLoader provideMinecraftLoader(UserProfileProvider p, DownloadType type) throws IllegalStateException;

    /**
     * Rename version
     * @param from The old name
     * @param to The new name
     * @return Is the action successful?
     */
    public abstract boolean renameVersion(String from, String to);

    /**
     * Remove the given version from disk.
     * @param a the version name
     * @return Is the action successful?
     */
    public abstract boolean removeVersionFromDisk(String a);

    /**
     * Redownload the Minecraft json of the given version.
     * @param a the given version name
     * @return Is the action successful?
     */
    public abstract boolean refreshJson(String a);

    /**
     * Redownload the Asset index json of the given version.
     * @param a the given version name
     * @return Is the action successful?
     */
    public abstract boolean refreshAssetsIndex(String a);

    /**
     * Choose a version randomly.
     * @return the version
     */
    public abstract MinecraftVersion getOneVersion();

    /**
     * All Minecraft version in this profile.
     * @return the collection of all Minecraft version
     */
    public abstract Collection<MinecraftVersion> getVersions();

    /**
     * Get the Minecraft json instance of given version.
     * @param id the given version name
     * @return the Minecraft json instance
     */
    public abstract MinecraftVersion getVersionById(String id);

    /**
     * getVersions().size()
     * @return getVersions().size()
     */
    public abstract int getVersionCount();

    /**
     * Refind the versions in this profile.
     */
    public abstract void refreshVersions();

    /**
     * Install a new version to this profile.
     * @param version the new version name
     * @param type where to download
     * @return Is the action successful?
     */
    public abstract boolean install(String version, DownloadType type);

    /**
     * When GameLauncher launches the Minecraft, this function will be called.
     */
    public abstract void onLaunch();

}
