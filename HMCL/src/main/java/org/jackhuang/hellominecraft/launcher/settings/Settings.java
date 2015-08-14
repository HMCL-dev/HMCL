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
package org.jackhuang.hellominecraft.launcher.settings;

import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.Main;
import org.jackhuang.hellominecraft.utils.CollectionUtils;
import org.jackhuang.hellominecraft.utils.system.FileUtils;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import org.jackhuang.hellominecraft.utils.system.MessageBox;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.utils.UpdateChecker;
import org.jackhuang.hellominecraft.utils.VersionNumber;
import org.jackhuang.hellominecraft.utils.system.Java;
import org.jackhuang.hellominecraft.utils.system.OS;

/**
 *
 * @author huangyuhui
 */
public final class Settings {

    public static final String DEFAULT_PROFILE = "Default";

    public static final File settingsFile = new File(IOUtils.currentDir(), "hmcl.json");

    private static boolean isFirstLoad;
    private static final Config settings;
    public static final UpdateChecker UPDATE_CHECKER;
    public static final List<Java> JAVA;

    public static Config getInstance() {
        return settings;
    }

    public static boolean isFirstLoad() {
        return isFirstLoad;
    }

    static {
        settings = initSettings();
        if (!getProfiles().containsKey(DEFAULT_PROFILE))
            getProfiles().put(DEFAULT_PROFILE, new Profile());

        for (Profile e : getProfiles().values())
            e.checkFormat();

        UPDATE_CHECKER = new UpdateChecker(new VersionNumber(Main.firstVer, Main.secondVer, Main.thirdVer),
                "hmcl", settings.isCheckUpdate(), () -> Main.invokeUpdate());

        List<Java> temp = new ArrayList<>();
        temp.add(new Java("Default", System.getProperty("java.home")));
        temp.add(new Java("Custom", null));
        if (OS.os() == OS.WINDOWS)
            temp.addAll(Java.queryAllJavaHomeInWindowsByReg());
        if (OS.os() == OS.OSX)
            temp.addAll(Java.queryAllJDKInMac());
        JAVA = Collections.unmodifiableList(temp);
    }

    private static Config initSettings() {
        Config c = new Config();
        if (settingsFile.exists()) {
            try {
                String str = FileUtils.readFileToString(settingsFile);
                if (str == null || str.trim().equals(""))
                    HMCLog.log("Settings file is empty, use the default settings.");
                else {
                    Config d = C.gsonPrettyPrinting.fromJson(str, Config.class);
                    if (d != null) c = d;
                }
                HMCLog.log("Initialized settings.");
            } catch (IOException | JsonSyntaxException e) {
                HMCLog.warn("Something happened wrongly when load settings.", e);
                if (MessageBox.Show(C.i18n("settings.failed_load"), MessageBox.YES_NO_OPTION) == MessageBox.NO_OPTION) {
                    HMCLog.err("Cancelled loading settings.");
                    System.exit(1);
                }
            }
            isFirstLoad = StrUtils.isBlank(c.getUsername());
        } else {
            HMCLog.log("No settings file here, may be first loading.");
            isFirstLoad = true;
        }
        return c;
    }

    public static void save() {
        try {
            FileUtils.write(settingsFile, C.gsonPrettyPrinting.toJson(settings));
        } catch (IOException ex) {
            HMCLog.err("Failed to save config", ex);
        }
    }

    public static Profile getProfile(String name) {
        return getProfiles().get(name);
    }

    public static Map<String, Profile> getProfiles() {
        return settings.getConfigurations();
    }

    public static void setProfile(Profile ver) {
        getProfiles().put(ver.getName(), ver);
    }

    public static Collection<Profile> getProfilesFiltered() {
        return CollectionUtils.sortOut(getProfiles().values(), (t) -> t != null && t.getName() != null);
    }

    public static Profile getOneProfile() {
        return settings.getConfigurations().firstEntry().getValue();
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
