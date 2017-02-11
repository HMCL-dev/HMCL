/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui
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
package org.jackhuang.hellominecraft.launcher.setting;

import org.jackhuang.hellominecraft.launcher.core.download.DownloadType;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.jackhuang.hellominecraft.launcher.core.auth.IAuthenticator;
import org.jackhuang.hellominecraft.lookandfeel.Theme;
import org.jackhuang.hellominecraft.util.EventHandler;
import org.jackhuang.hellominecraft.util.sys.JdkVersion;
import org.jackhuang.hellominecraft.util.sys.OS;

/**
 *
 * @author huangyuhui
 */
public final class Config implements Cloneable {

    @SerializedName("last")
    private String last;
    @SerializedName("bgpath")
    private String bgpath;
    @SerializedName("clientToken")
    private final String clientToken;
    @SerializedName("proxyHost")
    private String proxyHost;
    @SerializedName("proxyPort")
    private String proxyPort;
    @SerializedName("proxyUserName")
    private String proxyUserName;
    @SerializedName("proxyPassword")
    private String proxyPassword;
    @SerializedName("enableShadow")
    private boolean enableShadow;
    @SerializedName("enableBlur")
    private boolean enableBlur;
    @SerializedName("enableAnimation")
    private boolean enableAnimation;
    @SerializedName("decorated")
    private boolean decorated;
    @SerializedName("theme")
    private int theme;
    @SerializedName("java")
    private List<JdkVersion> java;
    @SerializedName("localization")
    private String localization;
    @SerializedName("logintype")
    private int logintype;
    @SerializedName("downloadtype")
    private int downloadtype;
    @SerializedName("configurations")
    private TreeMap<String, Profile> configurations;
    @SerializedName("auth")
    @SuppressWarnings("FieldMayBeFinal")
    private Map<String, Map> auth;

    public List<JdkVersion> getJava() {
        return java == null ? java = new ArrayList<>() : java;
    }

    public transient final EventHandler<Theme> themeChangedEvent = new EventHandler<>(this);
    public transient final EventHandler<DownloadType> downloadTypeChangedEvent = new EventHandler<>(this);
    public transient final EventHandler<IAuthenticator> authChangedEvent = new EventHandler<>(this);

    public Theme getTheme() {
        if (theme >= Theme.values().length)
            theme = 0;
        return Theme.values()[theme];
    }

    public void setTheme(int theme) {
        this.theme = theme;
        themeChangedEvent.execute(getTheme());
        Settings.save();
    }

    public boolean isDecorated() {
        return decorated;
    }

    public void setDecorated(boolean decorated) {
        this.decorated = decorated;
    }

    public boolean isEnableShadow() {
        return enableShadow;
    }

    public void setEnableShadow(boolean enableShadow) {
        this.enableShadow = enableShadow;
        Settings.save();
    }

    public boolean isEnableAnimation() {
        return enableAnimation;
    }

    public void setEnableAnimation(boolean enableAnimation) {
        this.enableAnimation = enableAnimation;
        Settings.save();
    }

    public boolean isEnableBlur() {
        return enableBlur;
    }

    public void setEnableBlur(boolean enableBlur) {
        this.enableBlur = enableBlur;
        Settings.save();
    }

    public String getLast() {
        if (last == null)
            last = Settings.DEFAULT_PROFILE;
        return last;
    }

    public void setLast(String last) {
        this.last = last;
        Settings.onProfileChanged();
        Settings.save();
    }

    public String getBgpath() {
        return bgpath;
    }

    public void setBgpath(String bgpath) {
        this.bgpath = bgpath;
        Settings.save();
    }

    public String getClientToken() {
        return clientToken;
    }

    public IAuthenticator getAuthenticator() {
        return IAuthenticator.LOGINS.get(getLoginType());
    }

    public int getLoginType() {
        if (logintype < 0 || logintype >= IAuthenticator.LOGINS.size())
            logintype = 0;
        return logintype;
    }

    public void setLoginType(int logintype) {
        if (logintype < 0 || logintype >= IAuthenticator.LOGINS.size())
            return;
        this.logintype = logintype;
        authChangedEvent.execute(IAuthenticator.LOGINS.get(logintype));
        Settings.save();
    }

    public int getDownloadType() {
        return downloadtype;
    }

    public void setDownloadType(int downloadtype) {
        this.downloadtype = downloadtype;
        downloadTypeChangedEvent.execute(getDownloadSource());
        Settings.save();
    }

    public TreeMap<String, Profile> getConfigurations() {
        if (configurations == null)
            configurations = new TreeMap<>();
        if (configurations.isEmpty()) {
            Profile profile = new Profile();
            configurations.put(profile.getName(), profile);
        }
        return configurations;
    }

    public Map getAuthenticatorConfig(String authId) {
        return auth.get(authId);
    }

    public void setAuthenticatorConfig(String authId, Map map) {
        auth.put(authId, map);
        Settings.save();
    }

    public Config() {
        clientToken = UUID.randomUUID().toString();
        logintype = downloadtype = 0;
        enableAnimation = enableBlur = enableShadow = true;
        theme = 4;
        decorated = OS.os() == OS.LINUX;
        auth = new HashMap<>();
    }

    public DownloadType getDownloadSource() {
        if (downloadtype >= DownloadType.values().length || downloadtype < 0) {
            downloadtype = 0;
            Settings.save();
        }
        return DownloadType.values()[downloadtype];
    }

    public String getProxyHost() {
        return proxyHost == null ? "" : proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
        Settings.save();
    }

    public String getProxyPort() {
        return proxyPort == null ? "" : proxyPort;
    }

    public void setProxyPort(String proxyPort) {
        this.proxyPort = proxyPort;
        Settings.save();
    }

    public String getProxyUserName() {
        return proxyUserName == null ? "" : proxyUserName;
    }

    public void setProxyUserName(String proxyUserName) {
        this.proxyUserName = proxyUserName;
        Settings.save();
    }

    public String getProxyPassword() {
        return proxyPassword == null ? "" : proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
        Settings.save();
    }

    public String getLocalization() {
        return localization;
    }

    public void setLocalization(String localization) {
        this.localization = localization;
        Settings.save();
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new Error(e);
        }
    }

}
