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
package org.jackhuang.hmcl.core.install.optifine;

/**
 *
 * @author huangyuhui
 */
public class OptiFineVersion {

    private String dl, ver, date, mirror, mcversion;
    public String patch, type; // For BMCLAPI2.

    public String getDownloadLink() {
        return dl;
    }

    public void setDownloadLink(String dl) {
        this.dl = dl;
    }

    public String getVersion() {
        return ver;
    }

    public void setVersion(String ver) {
        this.ver = ver;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getMirror() {
        return mirror;
    }

    public void setMirror(String mirror) {
        this.mirror = mirror;
    }

    public String getMCVersion() {
        return mcversion;
    }

    public void setMCVersion(String mcver) {
        this.mcversion = mcver;
    }
}
