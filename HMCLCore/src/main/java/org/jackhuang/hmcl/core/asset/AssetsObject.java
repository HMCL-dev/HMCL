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
package org.jackhuang.hmcl.core.asset;

/**
 *
 * @author huangyuhui
 */
public class AssetsObject {

    private String hash;
    private long size;

    public void setHash(String hash) {
        this.hash = hash;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public AssetsObject(String hash, long size) {
        this.hash = hash;
        this.size = size;
    }

    public String getHash() {
        return this.hash;
    }

    public long getSize() {
        return this.size;
    }
    
    public String getLocation() {
        return hash.substring(0, 2) + "/" + hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AssetsObject that = (AssetsObject) o;
        if (this.size != that.size)
            return false;
        return this.hash.equals(that.hash);
    }

    @Override
    public int hashCode() {
        int result = this.hash.hashCode();
        result = 31 * result + (int) (this.size ^ this.size >>> 32);
        return result;
    }
}
