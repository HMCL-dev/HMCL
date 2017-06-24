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
package org.jackhuang.hmcl.setting;

import org.jackhuang.hmcl.core.download.DownloadType;
import com.google.gson.annotations.SerializedName;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.jackhuang.hmcl.core.auth.AbstractAuthenticator;
import org.jackhuang.hmcl.laf.LAFTheme;
import org.jackhuang.hmcl.api.HMCLApi;
import org.jackhuang.hmcl.api.event.config.AuthenticatorChangedEvent;
import org.jackhuang.hmcl.api.event.config.DownloadTypeChangedEvent;
import org.jackhuang.hmcl.api.event.config.ThemeChangedEvent;
import org.jackhuang.hmcl.core.MCUtils;
import org.jackhuang.hmcl.util.sys.JdkVersion;
import org.jackhuang.hmcl.util.sys.OS;
import org.jackhuang.hmcl.api.auth.IAuthenticator;
import org.jackhuang.hmcl.api.ui.Theme;

/**
 *
 * @author huangyuhui
 */
public final class Config implements Cloneable {

    @SerializedName("last")
    private String last;
    @SerializedName("bgpath")
    private String bgpath;
    @SerializedName("commonpath")
    private String commonpath;
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
    private String theme;
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
    private Map<String, Map> auth;
    @SerializedName("fontFamily")
    private String fontFamily;
    @SerializedName("fontSize")
    private int fontSize;

    public Config() {
        clientToken = UUID.randomUUID().toString();
        logintype = downloadtype = 0;
        enableAnimation = enableBlur = true;
        if (OS.os() == OS.WINDOWS)
            enableShadow = true;
        theme = LAFTheme.BLUE.id;
        decorated = OS.os() == OS.LINUX;
        auth = new HashMap<>();
        Font font = Font.decode("Consolas");
        if (font == null)
            font = Font.decode("Monospace");
        if (font != null)
            fontFamily = font.getFamily();
        fontSize = 12;
        commonpath = MCUtils.getLocation().getPath();
    }

    public List<JdkVersion> getJava() {
        return java == null ? java = new ArrayList<>() : java;
    }

    public Theme getTheme() {
        if (!Theme.THEMES.containsKey(theme))
            theme = LAFTheme.BLUE.id;
        return Theme.THEMES.get(theme);
    }

    public void setTheme(String theme) {
        this.theme = theme;
        HMCLApi.EVENT_BUS.fireChannel(new ThemeChangedEvent(this, getTheme()));
        Settings.save();
    }

    public boolean isDecorated() {
        return decorated;
    }

    public void setDecorated(boolean decorated) {
        this.decorated = decorated;
    }

    public boolean isEnableShadow() {
        return enableShadow && OS.os() == OS.WINDOWS;
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

    /**
     * Last selected profile name.
     */
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

    public String getCommonpath() {
        return commonpath;
    }

    public void setCommonpath(String commonpath) {
        this.commonpath = commonpath;
        Settings.save();
    }

    public String getClientToken() {
        return clientToken;
    }

    public IAuthenticator getAuthenticator() {
        return AbstractAuthenticator.LOGINS.get(getLoginType());
    }

    public int getLoginType() {
        if (logintype < 0 || logintype >= AbstractAuthenticator.LOGINS.size())
            logintype = 0;
        return logintype;
    }

    public void setLoginType(int logintype) {
        if (logintype < 0 || logintype >= AbstractAuthenticator.LOGINS.size())
            return;
        this.logintype = logintype;
        HMCLApi.EVENT_BUS.fireChannel(new AuthenticatorChangedEvent(this, AbstractAuthenticator.LOGINS.get(logintype)));
        Settings.save();
    }

    public int getDownloadType() {
        return downloadtype;
    }

    public void setDownloadType(int downloadtype) {
        this.downloadtype = downloadtype;
        HMCLApi.EVENT_BUS.fireChannel(new DownloadTypeChangedEvent(this, getDownloadSource().name()));
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

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
        Settings.save();
    }
    
    public Font getConsoleFont() {
        return Font.decode(fontFamily + "-" + fontSize);
    }

    public String getFontFamily() {
        return Font.decode(fontFamily).getFamily();
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = Font.decode(fontFamily).getFamily(); // avoid invalid font family
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
