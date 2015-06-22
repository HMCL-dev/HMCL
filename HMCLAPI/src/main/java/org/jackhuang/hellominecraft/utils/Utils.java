/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

import com.google.gson.GsonBuilder;
import com.sun.management.OperatingSystemMXBean;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.Random;
import javax.swing.ImageIcon;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;

/**
 * @author hyh
 */
public final class Utils {
    
    private static final GsonBuilder gsonBuilder = new GsonBuilder().setPrettyPrinting();
    public static GsonBuilder getDefaultGsonBuilder() {
        return gsonBuilder;
    }

    public static String[] getURL() {
        URL[] urls = ((URLClassLoader) Utils.class.getClassLoader()).getURLs();
        String[] urlStrings = new String[urls.length];
        for (int i = 0; i < urlStrings.length; i++) {
            try {
                urlStrings[i] = URLDecoder.decode(urls[i].getPath(), "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                HMCLog.warn("Unsupported UTF-8 encoding", ex);
            }
        }
        return urlStrings;
    }

    public static void addDir(String s) throws IOException {
        try {
            Field field = ClassLoader.class.getDeclaredField("usr_paths");
            field.setAccessible(true);
            String[] paths = (String[]) field.get(null);
            for (int i = 0; i < paths.length; i++) {
                if (s.equals(paths[i])) {
                    return;
                }
            }
            String[] tmp = new String[paths.length + 1];
            System.arraycopy(paths, 0, tmp, 0, paths.length);
            tmp[paths.length] = s;
            field.set(null, tmp);
        } catch (IllegalAccessException e) {
            throw new IOException("Failed to get permissions to set library path");
        } catch (NoSuchFieldException e) {
            throw new IOException("Failed to get field handle to set library path");
        }
    }
    
    public static int getSuggestedMemorySize() {
	try {
	    OperatingSystemMXBean osmb = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
	    int memory = (int)(osmb.getTotalPhysicalMemorySize() / 1024 / 1024) / 4;
	    memory = Math.round((float)memory/128.0f)*128;
	    return memory;
	} catch(Throwable t) {
	    HMCLog.warn("Failed to get total memory size, use 1024MB.", t);
	    return 1024;
	}
    }
    
    public static void setClipborad(String text) {
	Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }
    
    public static boolean openLink(String url) {
	try {
	    Desktop.getDesktop().browse(new URI(url));
	    return true;
	} catch (Exception ex) {
	    HMCLog.warn("Failed to open link:" + url, ex);
	    return false;
	}
    }

    public static void openFolder(File f) {
        try {
            f.mkdirs();
            java.awt.Desktop.getDesktop().open(f);
        } catch (Exception ex) {
            MessageBox.Show(C.i18n("message.cannot_open_explorer") + ex.getMessage());
	    HMCLog.warn("Failed to open folder:" + f, ex);
        }
    }
    
    public static ImageIcon scaleImage(ImageIcon i, int x, int y) {
        return new ImageIcon(i.getImage().getScaledInstance(x, y, Image.SCALE_SMOOTH));
    }
    
    public static ImageIcon searchBackgroundImage(ImageIcon background, String bgpath, int width, int height) {
        Random r = new Random();
        boolean loaded = false;
        
        // bgpath
	if (StrUtils.isNotBlank(bgpath) && !loaded) {
	    String[] backgroundPath = bgpath.split(";");
            if(backgroundPath.length > 0) {
                int index = r.nextInt(backgroundPath.length);
                background = new ImageIcon(Toolkit.getDefaultToolkit().getImage(backgroundPath[index]).getScaledInstance(width, height, Image.SCALE_DEFAULT));
                HMCLog.log("Prepared background image in bgpath.");
                loaded = true;
            }
	}
        
        // bgskin
        if (!loaded) {
	    File backgroundImageFile = new File("bg");
	    if (backgroundImageFile.exists() && backgroundImageFile.isDirectory()) {
		String[] backgroundPath = backgroundImageFile.list();
                if(backgroundPath.length > 0) {
                    int index = r.nextInt(backgroundPath.length);
                    background = new ImageIcon(Toolkit.getDefaultToolkit().getImage("bg" + File.separator + backgroundPath[index]).getScaledInstance(width, height, Image.SCALE_DEFAULT));
                    HMCLog.log("Prepared background image in bgskin folder.");
                    loaded = true;
                }
            }
	}
        
        // background.png
        if (!loaded) {
            File backgroundImageFile = new File("background.png");
            if (backgroundImageFile.exists()) {
                loaded = true;
		background = new ImageIcon(Toolkit.getDefaultToolkit().getImage(backgroundImageFile.getAbsolutePath()).getScaledInstance(width, height, Image.SCALE_DEFAULT));
                HMCLog.log("Prepared background image in background.png.");
            }
	}
        
        // background.jpg
        if (!loaded) {
            File backgroundImageFile = new File("background.jpg");
            if (backgroundImageFile.exists()) {
                loaded = true;
		background = new ImageIcon(Toolkit.getDefaultToolkit().getImage(backgroundImageFile.getAbsolutePath()).getScaledInstance(width, height, Image.SCALE_DEFAULT));
                HMCLog.log("Prepared background image in background.jpg.");
            }
	}
        
        return background;
    }
    
    /**
     * In order to fight against the permission manager.
     */
    public static void shutdownForcely() {
        try {
            Class z = Class.forName("java.lang.Shutdown");
            Method exit = z.getDeclaredMethod("exit", int.class);
            exit.setAccessible(true);
            exit.invoke(z, 0);
        } catch(Exception e) {
            MessageBox.Show(C.i18n("launcher.exit_failed"));
            e.printStackTrace();
        }
    }
}
