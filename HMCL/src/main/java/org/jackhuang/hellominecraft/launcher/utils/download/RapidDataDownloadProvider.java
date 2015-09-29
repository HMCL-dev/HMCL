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
package org.jackhuang.hellominecraft.launcher.utils.download;

/**
 *
 * @author huangyuhui
 */
public class RapidDataDownloadProvider extends MojangDownloadProvider {

    @Override
    public String getAssetsDownloadURL() {
        return "http://mirrors.rapiddata.org/resources.download.minecraft.net/";
    }

    @Override
    public String getLibraryDownloadURL() {
        return "http://mirrors.rapiddata.org/libraries.minecraft.net";
    }

    @Override
    public String getIndexesDownloadURL() {
        return "http://mirrors.rapiddata.org/Minecraft.Download/indexes/";
    }

    @Override
    public String getVersionsDownloadURL() {
        return "http://mirrors.rapiddata.org/Minecraft.Download/versions/";
    }

    @Override
    public String getVersionsListDownloadURL() {
        return "http://mirrors.rapiddata.org/Minecraft.Download/versions/versions.json";
    }
    
    @Override
    public String getParsedLibraryDownloadURL(String str) {
        return str == null ? null : str.replace("http://files.minecraftforge.net/maven", "http://mirrors.rapiddata.org/forge/maven");
    }
    
    
}
