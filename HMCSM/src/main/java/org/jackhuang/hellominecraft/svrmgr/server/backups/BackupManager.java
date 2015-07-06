/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.server.backups;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.utils.system.Compressor;
import org.jackhuang.hellominecraft.svrmgr.settings.SettingsManager;
import org.jackhuang.hellominecraft.svrmgr.utils.Utilities;

/**
 *
 * @author huangyuhui
 */
public class BackupManager {
    
    public static String backupDir() {
        return  Utilities.getGameDir() + "backups-HMCSM" + File.separator;
    }
    
    public static ArrayList<String> getBackupList() {
        String gameDir = backupDir();
        return Utilities.findAllFile(new File(gameDir));
    }
    
    public static void addWorldBackup(final String folder) {
        new File(backupDir()).mkdirs();
        Thread t = new Thread() {
            @Override
            public void run() {
		try {
		    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
		    Compressor.zip(Utilities.getGameDir() + folder + File.separator,
			    backupDir() + "world+" + f.format(new Date()) + "+" + folder + ".zip");
		} catch (IOException ex) {
		    HMCLog.warn("Failed to compress world pack.", ex);
		}
            }
        };
        t.start();
    }
    
    public static ArrayList<String> findAllWorlds() {
        String gameDir = Utilities.getGameDir();
        ArrayList<String> folders = Utilities.findAllDir(new File(gameDir));
        ArrayList<String> result = new ArrayList<String>();
        for(String folder : folders) {
            String worldPath = gameDir + folder + File.separator;
            ArrayList<String> files = Utilities.findAllFile(new File(worldPath));
            if(files.contains("level.dat")) {
                result.add(folder);
            }
        }
        return result;
    }
    
    public static void restoreBackup(String backupFile) {
	try {
	    File file = new File(backupFile);
	    String name = Utilities.trimExtension(file.getName());
	    String[] info = name.split("\\+");
	    String folder = info[2];
	    File world = new File(Utilities.getGameDir() + folder + File.separator);
	    Utilities.deleteAll(world);
	    world.mkdirs();
	    Compressor.unzip(backupFile, world.getAbsolutePath());
	} catch (IOException ex) {
	    HMCLog.warn("Failed to decompress world pack.", ex);
	}
    }
    
    public static void backupAllWorlds() {
        ArrayList<String> al = findAllWorlds();
        for(String world : al) {
            if(!SettingsManager.settings.inactiveWorlds.contains(world))
                addWorldBackup(world);
        }
    }
    
    public static void backupAllPlugins() {
        new File(backupDir()).mkdirs();
        Thread t = new Thread() {
            @Override
            public void run() {
		try {
		    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
		    Compressor.zip(Utilities.getGameDir() + "plugins" + File.separator,
			    backupDir() + "plugin+" + f.format(new Date()) + "+plugins.zip");
		} catch (IOException ex) {
		    HMCLog.warn("Failed to compress world pack with plugins.", ex);
		}
            }
        };
        t.start();
    }
    
}
