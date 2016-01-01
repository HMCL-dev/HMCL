/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher;

import org.jackhuang.hellominecraftlauncher.apis.LogUtils;
import org.jackhuang.hellominecraftlauncher.apis.DoneListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jackhuang.hellominecraftlauncher.apis.handlers.Login;
import org.jackhuang.hellominecraftlauncher.apis.handlers.LoginInfo;
import org.jackhuang.hellominecraftlauncher.apis.handlers.LoginResult;
import org.jackhuang.hellominecraftlauncher.apis.PluginHandlerType;
import org.jackhuang.hellominecraftlauncher.apis.version.MinecraftLibrary;
import org.jackhuang.hellominecraftlauncher.loaders.MinecraftLoader;
import org.jackhuang.hellominecraftlauncher.loaders.MinecraftOldLoader;
import org.jackhuang.hellominecraftlauncher.plugin.PluginHandler;
import org.jackhuang.hellominecraftlauncher.utilities.SettingsManager;
import org.jackhuang.hellominecraftlauncher.settings.Version;
import org.jackhuang.hellominecraftlauncher.utilities.C;
import org.jackhuang.hellominecraftlauncher.apis.utils.MessageBox;
import org.jackhuang.hellominecraftlauncher.apis.utils.OperatingSystems;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;
import org.jackhuang.hellominecraftlauncher.apis.utils.Compressor;
import org.jackhuang.hellominecraftlauncher.apis.version.MinecraftVersion;

/**
 *
 * @author hyh
 */
public class GameLauncher {

    private static String makeCommand(List<String> cmd) {
        StringBuilder cmdbuf = new StringBuilder(120);
        for (int i = 0; i < cmd.size(); i++) {
            if (i > 0) {
                cmdbuf.append(' ');
            }
            String s = cmd.get(i);
            if (s.indexOf(' ') >= 0 || s.indexOf('\t') >= 0) {
                if (s.charAt(0) != '"') {
                    cmdbuf.append('"');
                    cmdbuf.append(s);
                    if (s.endsWith("\\")) {
                        cmdbuf.append("\\");
                    }
                    cmdbuf.append('"');
                } else if (s.endsWith("\"")) {
                    /* The argument has already been quoted. */
                    cmdbuf.append(s);
                } else {
                    /* Unmatched quote for the argument. */
                    throw new IllegalArgumentException();
                }
            } else {
                cmdbuf.append(s);
            }
        }
        String str = cmdbuf.toString();

        return str;
    }
    Version get;
    LoginInfo info;
    Login login;
    ArrayList<ArrayList<DoneListener<List, GameLauncher>>> listeners;

    public GameLauncher(Version version, LoginInfo info, int loginType) {
        this.get = version;
        this.info = info;
        listeners = new ArrayList<ArrayList<DoneListener<List, GameLauncher>>>(101);
        for (int i = 0; i < 101; i++) {
            listeners.add(new ArrayList<DoneListener<List, GameLauncher>>());
        }
        this.login = (Login)PluginHandler.getPluginHandlers(PluginHandlerType.getType("LOGIN")).get(loginType);
    }

    private void executeEvent(int eventType, List p) {
        for (DoneListener<List, GameLauncher> dl : listeners.get(eventType)) {
            dl.onDone(p, this);
        }
    }

    public void addListener(int eventType, DoneListener<List, GameLauncher> dl) {
        listeners.get(eventType).add(dl);
    }

    public void makeLaunchCommand() {
        ArrayList al = new ArrayList();
        LoginResult result;
        if(info != null)
            result = login.login(info);
        else
            result = login.loginBySettings();
        if (result == null || result.success == false) {
            String error;
            if (result == null || result.error == null) {
                error = C.I18N.getString("登录失败：") + C.I18N.getString("插件错误");
            } else {
                error = C.I18N.getString("登录失败：") + result.error;
            }
            al.add(error);
            executeEvent(0, al);
            return;
        }

        String name = get.name;
        final String realpa = Utils.addSeparator(Utils.getGameDir(get, SettingsManager.settings.publicSettings.gameDir));

        if (get.isVer16) {
            String minecraftJar = realpa
                    + "versions" + File.separator + name + File.separator + name
                    + ".jar";
            String minecraftJarTo = realpa
                    + "versions" + File.separator + name + File.separator + name
                    + "-HMCL-MERGE-TEMP.jar";


            if (get.minecraftJar != null && !get.minecraftJar.isEmpty()) {
                String unzipDir = Utils.addSeparator(Utils.currentDir())
                        + "HMCL-MERGE-TEMP" + File.separator;
                Compressor.unzip(minecraftJar, unzipDir);
                for (int i = 0; i < get.minecraftJar.size(); i++) {
                    if (get.minecraftJarIsActive == null || get.minecraftJarIsActive.get(i)) {
                        Compressor.unzip(get.minecraftJar.get(i), unzipDir);
                    }
                }
                Compressor.zip(unzipDir, minecraftJarTo);
                Utils.deleteAll(new File(unzipDir));
                minecraftJar = minecraftJarTo;
            }

            MinecraftVersion mv = Utils.getMinecraftVersion(get, SettingsManager.settings.publicSettings.gameDir);
            if (mv.minimumLauncherVersion > Main.minimumLauncherVersion) {
                MessageBox.Show("对不起，本启动器现在可能不能启动这个版本的Minecraft，但启动器还是会尝试启动");
            }

            final MinecraftLoader ml = new MinecraftLoader(mv,
                    SettingsManager.settings.publicSettings, get, result,
                    minecraftJar);

            //Find assets

            //Find, download & unzip libraries
            MinecraftVersion v = (ml).getVersion();
            File file = new File(realpa + "versions" + File.separator + name + File.separator + name + "-natives");
            Utils.deleteAll(file);
            file.mkdirs();
            ArrayList<String[]> unzippings = new ArrayList<String[]>();
            ArrayList<String[]> downloadLibraries = new ArrayList<String[]>();

            for (int i = 0; i < v.libraries.size(); i++) {
                MinecraftLibrary l = v.libraries.get(i);
                if (l.allow()) {
                    File ff = new File(realpa + "libraries" + File.separator
                            + l.formatted);
                    if (!ff.exists()) {
                        new File(ff.getParent()).mkdirs();
                        final String location = l.formatted.replace('\\', '/');
                        String[] lib = new String[3];
                        lib[0] = location;
                        lib[1] = C.URL_LIBRARIES[SettingsManager.settings.downloadtype] + location;
                        lib[2] = ff.getPath();
                        downloadLibraries.add(lib);
                    }
                }
                if (l.natives != null && l.allow()) {
                    String[] unzip = new String[2];
                    unzip[0] = realpa + "libraries" + File.separator + l.formatted;
                    unzip[1] = realpa + "versions" + File.separator
                            + name + File.separator + name + "-natives";
                    unzippings.add(unzip);
                }
            }

            executeEvent(2, downloadLibraries);
            executeEvent(3, unzippings);
            executeEvent(100, ml.makeLaunchingCommand());
        } else {
            String minecraftJar = realpa + "bin" + File.separator + "minecraft.jar";
            String minecraftJarTo = realpa + "bin" + File.separator + "minecraft-HMCL-MERGE-TEMP.jar";

            if (get.minecraftJar != null && !get.minecraftJar.isEmpty()) {
                String unzipDir = Utils.addSeparator(Utils.currentDir())
                        + "HMCL-MERGE-TEMP" + File.separator;
                Compressor.unzip(minecraftJar, unzipDir);
                for (int i = 0; i < get.minecraftJar.size(); i++) {
                    if (get.minecraftJarIsActive == null || get.minecraftJarIsActive.get(i)) {
                        Compressor.unzip(get.minecraftJar.get(i), unzipDir);
                    }
                }
                Compressor.zip(unzipDir, minecraftJarTo);
                Utils.deleteAll(new File(unzipDir));
                minecraftJar = minecraftJarTo;
            }
            String command;
            MinecraftOldLoader loader = new MinecraftOldLoader(
                    SettingsManager.settings.publicSettings, get, result,
                    minecraftJar);
            command = makeCommand(loader.makeLaunchingCommand());
            al.add(command);
            executeEvent(100, al);
        }
    }

    /**
     * Launch the game "as soon as possible".
     * @param str launch command
     */
    public void launch(List str) {
        try {
            Process p;
            File dir = new File(Utils.getGameDir(get, SettingsManager.settings.publicSettings.gameDir));
            String appdata = dir.getParent();
            LogUtils.info("APPDATA: " + appdata);

            ProcessBuilder builder = new ProcessBuilder(str);
            builder.environment().put("APPDATA", appdata);
            builder.directory(dir);
            p = builder.start();
            ArrayList<JavaProcess> al = new ArrayList<JavaProcess>();
            al.add(new JavaProcess(str, p));
            executeEvent(4, al);
        } catch (IOException e) {
            e.printStackTrace();
            MessageBox.Show(C.I18N.getString("启动失败"));
        }
    }

    /**
     * According to the name...
     *
     * @param launcherName the name of launch bat/sh
     * @param str launch command
     */
    public void makeLauncher(String launcherName, List str) {

        OperatingSystems os = Utils.os();
        String gameDir = Utils.getGameDir(get, SettingsManager.settings.publicSettings.gameDir);
        File f;
        boolean isWin = os == OperatingSystems.WINDOWS;
        if (isWin) {
            f = new File(launcherName + ".bat");
        } else {
            f = new File(launcherName + ".sh");
        }
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), System.getProperty("sun.jnu.encoding", "gbk")));
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(GameLauncher.class.getName()).log(Level.SEVERE, null, ex);
            try {
                writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)));
            } catch (FileNotFoundException ex1) {
                throw new RuntimeException(ex1);
            }
        }
        if (isWin) {

            File dir = new File(gameDir);
            String appdata = dir.getParent();
            if (appdata != null) {
                try {
                    writer.write("set appdata=" + appdata);
                    writer.newLine();
                } catch (IOException ex) {
                    System.err.println("Failed to write.");
                    ex.printStackTrace();
                }
            }
        }
        try {
            writer.write(makeCommand(str));
            writer.close();
        } catch (IOException ex) {
            Logger.getLogger(GameLauncher.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (!isWin) {
            try {
                Runtime.getRuntime().exec("chmod +x launch.sh");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
