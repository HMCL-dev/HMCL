/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jackhuang.hellominecraftlauncher.apis.IPlugin;
import org.jackhuang.hellominecraftlauncher.utilities.C;
import org.jackhuang.hellominecraftlauncher.apis.utils.OperatingSystems;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;
import org.jackhuang.hellominecraftlauncher.plugin.PluginManager;

/**
 *
 * @author hyh
 */
public class Launcher {

    public static boolean disactivedMods = false;
    public static File gameDir = null;
    public static boolean ver16 = false, notMove = false;
    public static String name = null;

    public static void print(String s) {
        System.out.println(s);
    }
    
    private static File getLocation() {
        return gameDir == null ? gameDir = Utils.getLocation() : gameDir;
    }

    public static void main(String[] args) {

        try {
            print("*** " + Main.makeTitle() + " ***");

            Dimension dimension = null;
            boolean fullscreen = false;
            String[] inactiveExtMods = null;
            String[] inactiveCoreMods = null;
            String classPath = "";
            String mainClass = "net.minecraft.client.Minecraft";

            ArrayList<String> cmdList = new ArrayList<String>();
            int width = 854;
            int height = 480;

            getLocation();

            for (int n = 0; n < args.length; n++) {
                String s = args[n];
                if (s.startsWith("-cp=")) {
                    classPath = s.substring("-cp=".length());
                    print("  ClassPath: " + classPath);
                } else if (s.startsWith("-windowSize=")) {
                    String o = s.substring("-windowSize=".length());
                    dimension = Utils.parseDimension(o);

                    print("  WindowSize: " + (String) o);

                    if (dimension == null) {
                        continue;
                    }
                    width = dimension.width;
                    height = dimension.height;
                } else if (s.equals("-windowFullscreen")) {
                    fullscreen = true;
                    print("  Fullscreen: " + true);
                } else if (s.startsWith("-inactiveExtMods=")) {
                    s = s.substring("-inactiveExtMods=".length());
                    inactiveExtMods = Utils.tokenize(s, ";");
                    print("  InactiveExtMods: " + s);
                } else if (s.startsWith("-inactiveCoreMods=")) {
                    s = s.substring("-inactiveCoreMods=".length());
                    inactiveCoreMods = Utils.tokenize(s, ";");
                    print("  InactiveCoreMods: " + s);
                } else if (s.startsWith("-mainClass=")) {
                    mainClass = s.substring("-mainClass=".length());
                    print("  MainClass: " + mainClass);
                } else if (s.equals("-ver16")) {
                    ver16 = true;
                    print("  Type: 1.6");
                } else if (s.equals("-notMove")) {
                    notMove = true;
                    print("  Do not move mods: true");
                } else if (s.startsWith("-name=")) {
                    name = s.substring("-name=".length());
                    print("  Name: " + name);
                } else if (s.startsWith("-gameDir=")) {
                    gameDir = new File(s.substring("-gameDir=".length()));
                    print("  GameDir: " + gameDir.getPath());
                } else {
                    cmdList.add(s);
                }
            }
            if (fullscreen
                    ? cmdList.add("--fullscreen")
                    : (width > 0) && (height > 0)) {
                cmdList.add("--width=" + width);
                cmdList.add("--height=" + height);
            }

            String[] cmds = (String[]) cmdList.toArray(new String[cmdList.size()]);

            if (classPath == null) {
                print("Invalid launcher classpath: " + classPath);
                return;
            }

            String[] tokenized = Utils.tokenize(classPath, File.pathSeparator);
            int len = tokenized.length;
            File[] files = new File[len];

            URL[] urls = new URL[len];
            for (int j = 0; j < len; j++) {
                files[j] = new File(tokenized[j]);
                urls[j] = new File(tokenized[j]).toURI().toURL();
            }
            URLClassLoader loader = new URLClassLoader(urls);
            Class minecraft = loader.loadClass(mainClass);
            Method minecraftMain = minecraft.getMethod("main", new Class[]{String[].class});

            moveMods();
            moveModDir();
            disactiveMods(inactiveExtMods, inactiveCoreMods);
            addShutdownHook();
            
            System.out.println("Invoking minecraft main()");
            minecraftMain.invoke(null, new Object[]{cmds});

            addForgeModLoaderHandler();

            print("*** Main class main() finished ***");
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    private static void disactiveMods(String[] inactiveExtMods, String[] inactiveCoreMods) {
        disactiveModsByType(inactiveExtMods, "mods");
        disactiveModsByType(inactiveCoreMods, "coremods");

        disactivedMods = true;
    }

    private static void addShutdownHook() {
        ShutdownHook hook = new ShutdownHook();

        Thread thread = new Thread(hook);
        Runtime.getRuntime().addShutdownHook(thread);
    }

    private static void addForgeModLoaderHandler() {
        MinecraftHandler handler = new MinecraftHandler();
        Logger localLogger = Logger.getLogger("ForgeModLoader");
        localLogger.addHandler(handler);
    }

    private static void disactiveModsByType(String[] paramArrayOfString, String paramString) {
        restoreModsByType(paramString);

        print("Disable inactive mods: " + paramString);
        if ((paramArrayOfString == null) || (paramArrayOfString.length <= 0)) {
            return;
        }
        List<String> al = Arrays.asList(paramArrayOfString);
        File[] files = new File(getLocation(), paramString).listFiles();
        if (files == null) {
            print("  No mods: " + paramString);
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (!file.isDirectory()) {
                String name = file.getName();

                if ((!al.contains(name))
                        || ((!name.toLowerCase().endsWith(".zip")) && (!name.toLowerCase().endsWith(".jar")))) {
                    continue;
                }

                String newName = name + "x";
                File newFile = new File(file.getParentFile(), newName);

                if (newFile.exists()) {
                    newFile.delete();
                }
                if (file.renameTo(newFile)) {
                    print("  Disabled: " + name + ", new name: " + newFile.getName());
                } else {
                    print("  Can not disable: " + name);
                }
            }
        }
    }

    private static void restoreModsByType(String paramString) {
        print("Restoring disabled mods: " + paramString);
        File[] files = new File(getLocation(), paramString).listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
                continue;
            }
            String name = file.getName();
            String lowName = name.toLowerCase();
            if ((!lowName.endsWith(".zipx")) && (!lowName.endsWith(".jarx"))) {
                continue;
            }
            String newName = name.substring(0, name.length() - 1);

            File newFile = new File(file.getParentFile(), newName);
            if (newFile.exists()) {
                file.delete();
            } else {
                if (!file.renameTo(newFile)) {
                    print("Can not rename: " + file.getName() + " to: " + newFile.getName() + " in: " + file.getParent());
                }
            }
        }
    }

    static void restoreMods() {
        if (disactivedMods) {
            restoreModsByType("mods");
            restoreModsByType("coremods");
            disactivedMods = false;
        }
    }

    static void moveMods() {
        if(notMove) return;
        String realpa = Utils.addSeparator(gameDir.getPath());
        java.util.ResourceBundle bundle = C.I18N;
        moveModsByType(realpa, name, "mods", bundle.getString("复制普通模组失败: "));
        moveModsByType(realpa, name, "coremods", bundle.getString("复制核心模组失败: "));
        moveModsByType(realpa, name, "config", bundle.getString("复制配置文件失败: "));
    }

    static void moveModsByType(String realpa, String name, String type, String error) {
        if(notMove) return;
        File file = new File(realpa + "versions" + File.separator + name + File.separator + type);
        print("Moving mods: " + type);
        if (file.exists()) {
            File mf = new File(realpa + type);
            print("Deleting folder:" + mf.getAbsolutePath());
            Utils.deleteAll(mf);
            try {
                Utils.copyDirectiory(file, mf);
                print("Copying folder:" + file.getAbsolutePath());
            } catch (IOException e) {
                print(error + e.getMessage());
            }
        } else {
            print("Folder doesn't exist: " + file.getAbsolutePath());
        }
    }

    static void moveBackMods() {
        if(notMove) return;
        String realpa = Utils.addSeparator(gameDir.getPath());
        moveBackModsByType(realpa, "mods");
        moveBackModsByType(realpa, "coremods");
        moveBackModsByType(realpa, "config");
    }

    static void moveBackModsByType(String realpa, String type) {
        if(notMove) return;
        if (ver16) {
            File fmods = new File(realpa + type);
            if (fmods.exists()) {
                try {
                    Utils.copyDirectiory(fmods,
                            new File(realpa + "versions" + File.separator
                            + name + File.separator + type));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static void moveModDir() {
        if(notMove) return;
        String realpa = Utils.addSeparator(gameDir.getPath());
        try {
            print("Moving mod directory");
            Utils.copyDirectiory(realpa + "versions" + File.separator + name + File.separator + "moddir",
                    realpa);
        } catch (IOException ex) {
            print("Failed to move mod directory");
            Logger.getLogger(Launcher.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    static void moveBackModDir() {
        if(notMove) return;
        String realpa = Utils.addSeparator(gameDir.getPath());
        File file = new File(realpa + "versions" + File.separator + name + File.separator + "moddir");
        if (file.exists()) {
            try {
                ArrayList<String> al = Utils.findAllDir(file);
                for (String s : al) {
                    File fileto = new File(file, s);
                    fileto.delete();
                    File moddir = new File(realpa + s);
                    Utils.copyDirectiory(moddir, fileto);
                }
                al = Utils.findAllFile(file);
                for (String s : al) {
                    File fileto = new File(file, s);
                    fileto.delete();
                    File moddir = new File(realpa + s);
                    Utils.copyFile(moddir, fileto);
                }
            } catch (IOException ex) {
                print("Failed to move back mod directory");
                Logger.getLogger(Launcher.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}