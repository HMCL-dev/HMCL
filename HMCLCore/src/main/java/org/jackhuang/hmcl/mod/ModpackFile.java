package org.jackhuang.hmcl.mod;

/**
 * Representing a file entry which allow modpack developer declare it's optional or not
 * */
public interface ModpackFile {
    /**
     * Get the file name for the file
     */
    String getFileName();
    /**
     * return if the file optional on the client side
     */
    boolean isOptional();
}
