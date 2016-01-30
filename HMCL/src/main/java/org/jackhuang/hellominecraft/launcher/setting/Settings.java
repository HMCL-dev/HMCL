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
import org.jackhuang.hellominecraft.launcher.core.download.DownloadType;
import org.jackhuang.hellominecraft.util.CollectionUtils;
import org.jackhuang.hellominecraft.util.Event;
import org.jackhuang.hellominecraft.util.system.FileUtils;
import org.jackhuang.hellominecraft.util.system.IOUtils;
import org.jackhuang.hellominecraft.util.MessageBox;
import org.jackhuang.hellominecraft.util.UpdateChecker;
import org.jackhuang.hellominecraft.util.VersionNumber;
import org.jackhuang.hellominecraft.util.func.Predicate;

/**
 *
 * @author huangyuhui
 */
public final class Settings {

    public static final String DEFAULT_PROFILE = "Default";

    public static final File SETTINGS_FILE = new File(IOUtils.currentDir(), "hmcl.json");

    private static final Config SETTINGS;
    public static final UpdateChecker UPDATE_CHECKER = new UpdateChecker(new VersionNumber(Main.VERSION_FIRST, Main.VERSION_SECOND, Main.VERSION_THIRD),
                                                                         "hmcl");

    public static Config getInstance() {
        return SETTINGS;
    }

    static {
        SETTINGS = initSettings();
        SETTINGS.downloadTypeChangedEvent.register(new Event<DownloadType>() {
            @Override
            public boolean call(Object sender, DownloadType t) {
                DownloadType.setSuggestedDownloadType(t);
                return true;
            }
        });
        DownloadType.setSuggestedDownloadType(SETTINGS.getDownloadSource());
        if (!getProfiles().containsKey(DEFAULT_PROFILE))
            getProfiles().put(DEFAULT_PROFILE, new Profile());

        for (Profile e : getProfiles().values()) {
            e.checkFormat();
            e.propertyChanged.register(new Event<String>() {
                @Override
                public boolean call(Object sender, String t) {
                    save();
                    return true;
                }
            });
        }
    }

    private static Config initSettings() {
        Config c = new Config();
        if (SETTINGS_FILE.exists())
            try {
                String str = FileUtils.readFileToString(SETTINGS_FILE);
                if (str == null || str.trim().equals(""))
                    HMCLog.log("Settings file is empty, use the default settings.");
                else {
                    Config d = C.gsonPrettyPrinting.fromJson(str, Config.class);
                    if (d != null)
                        c = d;
                }
                HMCLog.log("Initialized settings.");
            } catch (IOException | JsonSyntaxException e) {
                HMCLog.warn("Something happened wrongly when load settings.", e);
                if (MessageBox.Show(C.i18n("settings.failed_load"), MessageBox.YES_NO_OPTION) == MessageBox.NO_OPTION) {
                    HMCLog.err("Cancelled loading settings.");
                    System.exit(1);
                }
            }
        else
            HMCLog.log("No settings file here, may be first loading.");
        return c;
    }

    public static void save() {
        try {
            FileUtils.write(SETTINGS_FILE, C.gsonPrettyPrinting.toJson(SETTINGS));
        } catch (IOException ex) {
            HMCLog.err("Failed to save config", ex);
        }
    }

    public static Profile getProfile(String name) {
        if (name == null)
            return getProfiles().get("Default");
        return getProfiles().get(name);
    }

    public static Map<String, Profile> getProfiles() {
        return SETTINGS.getConfigurations();
    }

    public static void setProfile(Profile ver) {
        getProfiles().put(ver.getName(), ver);
    }

    public static Collection<Profile> getProfilesFiltered() {
        return CollectionUtils.map(getProfiles().values(), new Predicate<Profile>() {
                                   @Override
                                   public boolean apply(Profile t) {
                                       return t != null && t.getName() != null;
                                   }
                               });
    }

    public static Profile getOneProfile() {
        return SETTINGS.getConfigurations().firstEntry().getValue();
    }

    public static boolean trySetProfile(Profile ver) {
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
            MessageBox.Show(C.i18n("settings.cannot_remove_default_config"));
            return false;
        }
        return getProfiles().remove(ver) != null;
    }
}
