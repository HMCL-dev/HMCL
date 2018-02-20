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
package org.jackhuang.hmcl.download.forge;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.Validation;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class ForgeVersion implements Validation {

    private final String branch;
    private final String mcversion;
    private final String jobver;
    private final String version;
    private final int build;
    private final double modified;
    private final String[][] files;

    /**
     * No-arg constructor for Gson.
     */
    @SuppressWarnings("unused")
    public ForgeVersion() {
        this(null, null, null, null, 0, 0, null);
    }

    public ForgeVersion(String branch, String mcversion, String jobver, String version, int build, double modified, String[][] files) {
        this.branch = branch;
        this.mcversion = mcversion;
        this.jobver = jobver;
        this.version = version;
        this.build = build;
        this.modified = modified;
        this.files = files;
    }

    public String getBranch() {
        return branch;
    }

    public String getGameVersion() {
        return mcversion;
    }

    public String getJobver() {
        return jobver;
    }

    public String getVersion() {
        return version;
    }

    public int getBuild() {
        return build;
    }

    public double getModified() {
        return modified;
    }

    public String[][] getFiles() {
        return files;
    }

    @Override
    public void validate() throws JsonParseException {
        if (files == null)
            throw new JsonParseException("ForgeVersion files cannot be null");
        if (version == null)
            throw new JsonParseException("ForgeVersion version cannot be null");
        if (mcversion == null)
            throw new JsonParseException("ForgeVersion mcversion cannot be null");
    }

}
