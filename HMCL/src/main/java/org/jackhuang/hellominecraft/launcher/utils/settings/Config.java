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
package org.jackhuang.hellominecraft.launcher.utils.settings;

import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;
import com.google.gson.annotations.SerializedName;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.jackhuang.hellominecraft.utils.OS;

/**
 *
 * @author hyh
 */
public final class Config {
    @SerializedName("last")
    private String last;
    @SerializedName("bgpath")
    private String bgpath;
    @SerializedName("username")
    private String username;
    @SerializedName("clientToken")
    private String clientToken;
    @SerializedName("enableShadow")
    private boolean enableShadow;
    @SerializedName("theme")
    private int theme;

    public int getTheme() {
        return theme;
    }

    public void setTheme(int theme) {
        this.theme = theme;
        Settings.save();
    }

    public boolean isEnableShadow() {
        return enableShadow;
    }

    public void setEnableShadow(boolean enableShadow) {
        this.enableShadow = enableShadow;
        Settings.save();
    }

    public String getLast() {
        return last;
    }

    public void setLast(String last) {
        this.last = last;
        Settings.save();
    }

    public String getBgpath() {
        return bgpath;
    }

    public void setBgpath(String bgpath) {
        this.bgpath = bgpath;
        Settings.save();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
        Settings.save();
    }

    public String getClientToken() {
        return clientToken;
    }

    public int getLoginType() {
        return logintype;
    }

    public void setLoginType(int logintype) {
        this.logintype = logintype;
        Settings.save();
    }

    public int getDownloadType() {
        return downloadtype;
    }

    public void setDownloadType(int downloadtype) {
        this.downloadtype = downloadtype;
        Settings.save();
    }

    public TreeMap<String, Profile> getConfigurations() {
        if(configurations == null)
            configurations = new TreeMap<>();
        return configurations;
    }

    public boolean isCheckUpdate() {
        return checkUpdate;
    }

    public void setCheckUpdate(boolean checkUpdate) {
        this.checkUpdate = checkUpdate;
        Settings.save();
    }

    public Map getYggdrasilConfig() {
        return yggdrasil;
    }

    public void setYggdrasilConfig(Map yggdrasil) {
        this.yggdrasil = yggdrasil;
        Settings.save();
    }
    
    @SerializedName("logintype")
    private int logintype;
    @SerializedName("downloadtype")
    private int downloadtype;
    @SerializedName("configurations")
    private TreeMap<String, Profile> configurations;
    @SerializedName("checkUpdate")
    private boolean checkUpdate;
    @SerializedName("yggdrasil")
    private Map yggdrasil;
    
    public Config()
    {
        clientToken = UUID.randomUUID().toString();
        username = "";
        logintype = downloadtype = 0;
        checkUpdate = true;
	configurations = new TreeMap<>();
	Profile profile = new Profile();
	configurations.put(profile.getName(), profile);
        enableShadow = OS.os() == OS.WINDOWS;
        theme = 0;
    }

    public DownloadType getDownloadSource() {
        if(downloadtype >= DownloadType.values().length || downloadtype < 0) return null;
        return DownloadType.values()[downloadtype];
    }
}
