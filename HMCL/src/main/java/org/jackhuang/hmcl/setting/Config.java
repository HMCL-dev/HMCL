/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.setting;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.util.JavaVersion;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class Config {

    @SerializedName("last")
    private String selectedProfile = "";

    @SerializedName("bgpath")
    private String backgroundImage = null;

    @SerializedName("commonpath")
    private String commonDirectory = Main.MINECRAFT_DIRECTORY.getAbsolutePath();

    @SerializedName("proxyType")
    private int proxyType = 0;

    @SerializedName("proxyHost")
    private String proxyHost = null;

    @SerializedName("proxyPort")
    private String proxyPort = null;

    @SerializedName("proxyUserName")
    private String proxyUser = null;

    @SerializedName("proxyPassword")
    private String proxyPass = null;

    @SerializedName("theme")
    private String theme = null;

    @SerializedName("java")
    private List<JavaVersion> java = null;

    @SerializedName("localization")
    private String localization;

    @SerializedName("downloadtype")
    private int downloadType = 0;

    @SerializedName("configurations")
    private Map<String, Profile> configurations = new TreeMap<>();

    @SerializedName("accounts")
    private List<Map<Object, Object>> accounts = new LinkedList<>();

    @SerializedName("selectedAccount")
    private String selectedAccount = "";

    @SerializedName("fontFamily")
    private String fontFamily = "Consolas";

    @SerializedName("fontSize")
    private double fontSize = 12;

    @SerializedName("logLines")
    private int logLines = 100;

    public String getSelectedProfile() {
        return selectedProfile;
    }

    public void setSelectedProfile(String selectedProfile) {
        this.selectedProfile = selectedProfile;
        Settings.INSTANCE.save();
    }

    public String getBackgroundImage() {
        return backgroundImage;
    }

    public void setBackgroundImage(String backgroundImage) {
        this.backgroundImage = backgroundImage;
        Settings.INSTANCE.save();
    }

    public String getCommonDirectory() {
        return commonDirectory;
    }

    public void setCommonDirectory(String commonDirectory) {
        this.commonDirectory = commonDirectory;
        Settings.INSTANCE.save();
    }

    public int getProxyType() {
        return proxyType;
    }

    public void setProxyType(int proxyType) {
        this.proxyType = proxyType;
        Settings.INSTANCE.save();
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
        Settings.INSTANCE.save();
    }

    public String getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(String proxyPort) {
        this.proxyPort = proxyPort;
        Settings.INSTANCE.save();
    }

    public String getProxyUser() {
        return proxyUser;
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser = proxyUser;
        Settings.INSTANCE.save();
    }

    public String getProxyPass() {
        return proxyPass;
    }

    public void setProxyPass(String proxyPass) {
        this.proxyPass = proxyPass;
        Settings.INSTANCE.save();
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
        Settings.INSTANCE.save();
    }

    public List<JavaVersion> getJava() {
        return java;
    }

    public void setJava(List<JavaVersion> java) {
        this.java = java;
        Settings.INSTANCE.save();
    }

    public String getLocalization() {
        return localization;
    }

    public void setLocalization(String localization) {
        this.localization = localization;
        Settings.INSTANCE.save();
    }

    public int getDownloadType() {
        return downloadType;
    }

    public void setDownloadType(int downloadType) {
        this.downloadType = downloadType;
        Settings.INSTANCE.save();
    }

    public Map<String, Profile> getConfigurations() {
        return configurations;
    }

    public void setConfigurations(Map<String, Profile> configurations) {
        this.configurations = configurations;
        Settings.INSTANCE.save();
    }

    public List<Map<Object, Object>> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<Map<Object, Object>> accounts) {
        this.accounts = accounts;
        Settings.INSTANCE.save();
    }

    public String getSelectedAccount() {
        return selectedAccount;
    }

    public void setSelectedAccount(String selectedAccount) {
        this.selectedAccount = selectedAccount;
        Settings.INSTANCE.save();
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
        Settings.INSTANCE.save();
    }

    public double getFontSize() {
        return fontSize;
    }

    public void setFontSize(double fontSize) {
        this.fontSize = fontSize;
        Settings.INSTANCE.save();
    }

    public int getLogLines() {
        return logLines;
    }

    public void setLogLines(int logLines) {
        this.logLines = logLines;
    }
}
