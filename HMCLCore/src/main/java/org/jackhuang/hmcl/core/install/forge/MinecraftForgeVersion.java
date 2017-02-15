/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.core.install.forge;

/**
 *
 * @author huangyuhui
 */
public class MinecraftForgeVersion {

    public String branch, mcversion, jobver, version;
    public int build;
    public double modified;
    public String[][] files;

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getMCVersion() {
        return mcversion;
    }

    public void setMCVersion(String mcversion) {
        this.mcversion = mcversion;
    }

    public String getJobver() {
        return jobver;
    }

    public void setJobver(String jobver) {
        this.jobver = jobver;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getBuild() {
        return build;
    }

    public void setBuild(int build) {
        this.build = build;
    }

    public double getModified() {
        return modified;
    }

    public void setModified(double modified) {
        this.modified = modified;
    }

}
