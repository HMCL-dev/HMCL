/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.settings;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.utils.functions.DoneListener0;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.Main;
import org.jackhuang.hellominecraft.utils.tinystream.CollectionUtils;
import org.jackhuang.hellominecraft.utils.FileUtils;
import org.jackhuang.hellominecraft.utils.IOUtils;
import org.jackhuang.hellominecraft.utils.MessageBox;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.utils.UpdateChecker;
import org.jackhuang.hellominecraft.utils.VersionNumber;

/**
 *
 * @author hyh
 */
public final class Settings {
    public static final File settingsFile = new File(IOUtils.currentDir(), "hmcl.json");

    private static boolean isFirstLoad;
    private static Config settings;
    public static final UpdateChecker UPDATE_CHECKER;

    public static Config s() {
        return settings;
    }

    public static boolean isFirstLoad() {
        return isFirstLoad;
    }

    static {
        if (settingsFile.exists()) {
            try {
                String str = FileUtils.readFileToString(settingsFile);
                if (str == null || str.trim().equals("")) {
                    init();
		    
		    HMCLog.log("Settings file is empty, use the default settings.");
                } else {
                    settings = C.gsonPrettyPrinting.fromJson(str, Config.class);
                }
                HMCLog.log("Initialized settings.");
            } catch (Exception e) {
		    HMCLog.warn("Something happened wrongly when load settings.", e);
                if (MessageBox.Show(C.i18n("settings.failed_load"), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION) {
                    init();
                } else {
		    HMCLog.err("Cancelled loading settings.");
                    System.exit(1);
                }
            }
        } else {
	    HMCLog.log("No settings file here, may be first loading.");
	    
            isFirstLoad = true;
            init();
        }
	if(settings == null) init();
        isFirstLoad = StrUtils.isBlank(settings.getUsername());
	if(!settings.getConfigurations().containsKey("Default")) {
	    settings.getConfigurations().put("Default", new Profile());
	}
        
        UPDATE_CHECKER = new UpdateChecker(new VersionNumber(Main.firstVer, Main.secondVer, Main.thirdVer), "hmcl", settings.isCheckUpdate(), new DoneListener0() {

            @Override
            public void onDone() {
                Main.invokeUpdate();
            }
        });
    }

    public static void init() {
        settings = new Config();
        save();
    }

    public static void save() {
        try {
            FileUtils.write(settingsFile, C.gsonPrettyPrinting.toJson(settings));
        } catch (IOException ex) {
            HMCLog.err("Failed to save config", ex);
        }
    }

    public static Profile getVersion(String name) {
        if (settings == null) {
            return null;
        }
        if (settings.getConfigurations() == null) {
            return null;
        }
        return settings.getConfigurations().get(name);
    }
    
    public static Map<String, Profile> getVersions() {
	return settings.getConfigurations();
    }

    public static void setVersion(Profile ver) {
        if (ver == null) {
            return;
        }
        settings.getConfigurations().put(ver.getName(), ver);
    }
    
    public static Collection<Profile> getProfiles() {
	return CollectionUtils.sortOut(settings.getConfigurations().values(), (t) -> t != null && t.getName() != null);
    }
    
    public static Profile getOneProfile() {
        if(settings.getConfigurations().size() == 0) {
	    settings.getConfigurations().put("Default", new Profile());
        }
        return settings.getConfigurations().firstEntry().getValue();
    }

    public static boolean trySetVersion(Profile ver) {
        if (ver == null || ver.getName() == null) {
            return false;
        }
        if (settings.getConfigurations().containsKey(ver.getName())) {
            return false;
        }
        settings.getConfigurations().put(ver.getName(), ver);
        return true;
    }

    public static void delVersion(Profile ver) {
        delVersion(ver.getName());
    }

    public static void delVersion(String ver) {
        if (settings == null) return;
        settings.getConfigurations().remove(ver);
    }
}
