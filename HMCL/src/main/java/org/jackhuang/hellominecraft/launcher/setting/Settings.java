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

import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.launcher.Main;
import org.jackhuang.hellominecraft.launcher.core.MCUtils;
import org.jackhuang.hellominecraft.launcher.core.download.DownloadType;
import org.jackhuang.hellominecraft.util.CollectionUtils;
import org.jackhuang.hellominecraft.util.EventHandler;
import org.jackhuang.hellominecraft.util.system.FileUtils;
import org.jackhuang.hellominecraft.util.MessageBox;
import org.jackhuang.hellominecraft.util.UpdateChecker;

/**
 *
 * @author huangyuhui
 */
public final class Settings {

    public static final String DEFAULT_PROFILE = "Default";
    public static final String HOME_PROFILE = "Home";

    public static final File SETTINGS_FILE = new File("hmcl.json");

    private static final Config SETTINGS;
    public static final UpdateChecker UPDATE_CHECKER = new UpdateChecker(Main.getVersionNumber(), "hmcl");

    public static Config getInstance() {
        return SETTINGS;
    }

    static {
        SETTINGS = initSettings();
        SETTINGS.downloadTypeChangedEvent.register(DownloadType::setSuggestedDownloadType);
        DownloadType.setSuggestedDownloadType(SETTINGS.getDownloadSource());
        if (!getProfiles().containsKey(DEFAULT_PROFILE))
            getProfiles().put(DEFAULT_PROFILE, new Profile(DEFAULT_PROFILE));

        for (Map.Entry<String, Profile> entry : getProfiles().entrySet()) {
            Profile e = entry.getValue();
            e.setName(entry.getKey());
            e.checkFormat();
            e.propertyChanged.register(Settings::save);
        }
    }

    private static Config initSettings() {
        Config c = new Config();
        if (SETTINGS_FILE.exists())
            try {
                String str = FileUtils.read(SETTINGS_FILE);
                if (str == null || str.trim().equals(""))
                    HMCLog.log("Settings file is empty, use the default settings.");
                else {
                    Config d = C.GSON.fromJson(str, Config.class);
                    if (d != null)
                        c = d;
                }
                HMCLog.log("Initialized settings.");
            } catch (IOException | JsonSyntaxException e) {
                HMCLog.warn("Something happened wrongly when load settings.", e);
            }
        else {
            HMCLog.log("No settings file here, may be first loading.");
            if (!c.getConfigurations().containsKey(HOME_PROFILE))
                c.getConfigurations().put(HOME_PROFILE, new Profile(HOME_PROFILE, MCUtils.getLocation().getPath()));
        }
        return c;
    }

    public static void save() {
        try {
            FileUtils.write(SETTINGS_FILE, C.GSON.toJson(SETTINGS));
        } catch (IOException ex) {
            HMCLog.err("Failed to save config", ex);
        }
    }

    public static Profile getLastProfile() {
        if (!hasProfile(getInstance().getLast()))
            getInstance().setLast(DEFAULT_PROFILE);
        return getProfile(getInstance().getLast());
    }

    public static Profile getProfile(String name) {
        if (name == null)
            name = DEFAULT_PROFILE;
        Profile p = getProfiles().get(name);
        if (p == null)
            if (getProfiles().containsKey(DEFAULT_PROFILE))
                p = getProfiles().get(DEFAULT_PROFILE);
            else
                getProfiles().put(DEFAULT_PROFILE, p = new Profile());
        return p;
    }

    public static boolean hasProfile(String name) {
        if (name == null)
            name = DEFAULT_PROFILE;
        return getProfiles().containsKey(name);
    }

    public static Map<String, Profile> getProfiles() {
        return SETTINGS.getConfigurations();
    }

    public static void setProfile(Profile ver) {
        getProfiles().put(ver.getName(), ver);
    }

    public static Collection<Profile> getProfilesFiltered() {
        return CollectionUtils.map(getProfiles().values(), t -> t != null && t.getName() != null);
    }

    public static Profile getOneProfile() {
        return SETTINGS.getConfigurations().firstEntry().getValue();
    }

    public static boolean putProfile(Profile ver) {
        if (ver == null || ver.getName() == null || getProfiles().containsKey(ver.getName()))
            return false;
        getProfiles().put(ver.getName(), ver);
        return true;
    }

    public static boolean delProfile(Profile ver) {
        return delProfile(ver.getName());
    }

    public static boolean delProfile(String ver) {
        if (DEFAULT_PROFILE.equals(ver)) {
            MessageBox.show(C.i18n("settings.cannot_remove_default_config"));
            return false;
        }
        boolean notify = false;
        if (getLastProfile().getName().equals(ver))
            notify = true;
        boolean flag = getProfiles().remove(ver) != null;
        if (notify && flag)
            onProfileChanged();
        return flag;
    }

    public static final EventHandler<Profile> profileChangedEvent = new EventHandler(null);
    public static final EventHandler<Void> profileLoadingEvent = new EventHandler(null);

    static void onProfileChanged() {
        Profile p = getLastProfile();
        if (p == null)
            throw new Error("No profiles here, it should not happen");
        profileChangedEvent.execute(p);
        p.onSelected();
    }

    public static void onProfileLoading() {
        profileLoadingEvent.execute(null);
        onProfileChanged();
    }
}
