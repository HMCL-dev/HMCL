/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.settings;

/**
 *
 * @author hyh
 */
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.utils.FileUtils;
import org.jackhuang.hellominecraft.utils.IOUtils;

/**
 *
 * @author hyh
 */
public class SettingsManager {

    public static Settings settings;
    public static boolean isFirstLoad = false;
    static Gson gson;

    public static void load() {
	gson = new Gson();
	File file = new File(IOUtils.currentDir(), "hmcsm.json");
	if (file.exists()) {
	    try {
		String str = FileUtils.readFileToString(file);
		if (str == null || str.trim().equals("")) {
		    init();
		} else {
		    settings = gson.fromJson(str, Settings.class);
		}
	    } catch (IOException ex) {
		init();
	    }
	} else {
	    settings = new Settings();
	    save();
	}
    }

    public static void init() {
	settings = new Settings();
	isFirstLoad = true;
	save();
    }

    public static void save() {
	File f = new File(IOUtils.currentDir(), "hmcsm.json");
	try {
	    FileUtils.write(f, gson.toJson(settings));
	} catch (IOException ex) {
	    HMCLog.err("Failed to save settings.", ex);
	}
    }
}
