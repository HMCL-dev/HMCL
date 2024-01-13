package org.jackhuang.hmcl.mod;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Representing a file entry which allow modpack developer declare it's optional or not
 * */
public interface ModpackFile {
    /**
     * Get the file name for the file
     */
    String getFileName();
    /**
     * Return if the file optional on the client side
     */
    boolean isOptional();
    /**
     * Return the path of the file
     */
    String getPath();
    /**
     * Return the mod the file belongs to
     *
     * About null and Optional.empty():
     * If the file hasn't been queried from remote, the mod will be null
     * If the file has been queried from remote but not found, the mod will be Optional.empty()
     */
    @Nullable Optional<RemoteMod> getMod();
}
