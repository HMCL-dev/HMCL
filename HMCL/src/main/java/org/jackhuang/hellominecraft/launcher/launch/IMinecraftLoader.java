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
package org.jackhuang.hellominecraft.launcher.launch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.Launcher;
import org.jackhuang.hellominecraft.launcher.utils.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.utils.settings.Profile;
import org.jackhuang.hellominecraft.utils.FileUtils;
import org.jackhuang.hellominecraft.utils.JdkVersion;
import org.jackhuang.hellominecraft.utils.MathUtils;
import org.jackhuang.hellominecraft.utils.MessageBox;
import org.jackhuang.hellominecraft.utils.OS;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.utils.Utils;

/**
 *
 * @author hyh
 */
public abstract class IMinecraftLoader {

    protected File minecraftJar;
    protected Profile v;
    protected UserProfileProvider lr;
    protected File gameDir;
    protected IMinecraftProvider provider;

    public IMinecraftLoader(Profile ver, IMinecraftProvider provider, UserProfileProvider lr, File minecraftJar) {
        this.lr = lr;

        this.minecraftJar = minecraftJar;
        v = ver;
        this.provider = provider;
        gameDir = v.getCanonicalGameDirFile();
    }

    public void makeHeadCommand(List<String> res) {
        HMCLog.log("On making head command.");

        if (StrUtils.isNotBlank(v.getWrapperLauncher()))
            res.addAll(Arrays.asList(v.getWrapperLauncher().split(" ")));

        String str = v.getJavaDir();
        JdkVersion jv = null;
        File f = new File(str + ".hmc");
        try {
            String s = FileUtils.readFileToString(f);
            String[] strs = s.split("\n");
            if (str.length() >= 2)
                jv = new JdkVersion(strs[0], MathUtils.parseInt(strs[1], -1));
            else
                throw new IllegalStateException("The format of file: " + f + " is wrong: " + s);
        } catch (IOException | IllegalStateException e) {
            try {
                jv = JdkVersion.getJavaVersionFromExecutable(str);
                jv.write(f);
                if (!f.exists())
                    HMCLog.warn("Failed to load version from file " + f, e);
            } catch (Exception ex) {
                HMCLog.warn("Failed to read JDKVersion.", ex);
            }
        }
        res.add(str);

        if (v.hasJavaArgs())
            res.addAll(Arrays.asList(StrUtils.tokenize(v.getJavaArgs())));

        if (!v.isNoJVMArgs() && !(jv != null && jv.isEarlyAccess())) {
            res.add("-Xincgc");
            res.add("-XX:+UseConcMarkSweepGC");
            res.add("-XX:+CMSIncrementalMode");
            res.add("-XX:-UseAdaptiveSizePolicy");

            res.add("-Xmn128m");
        }

        if (jv != null && jv.is64Bit == 0 && OS.is64Bit())
            MessageBox.Show(C.i18n("advice.os64butjdk32"));

        if (!StrUtils.isBlank(v.getMaxMemory())) {
            int mem = MathUtils.parseMemory(v.getMaxMemory(), 2147483647);
            if (jv != null && jv.is64Bit == 0 && mem > 1024)
                MessageBox.Show(C.i18n("launch.too_big_memory_alloc_64bit"));
            else {
                long a = OS.getTotalPhysicalMemory() / 1024 / 1024;
                HMCLog.log("System Physical Memory: " + a);
                if (a > 0 && a < mem)
                    MessageBox.Show(C.i18n("launch.too_big_memory_alloc_free_space_too_low", a));
            }
            String a = "-Xmx" + v.getMaxMemory();
            if (MathUtils.canParseInt(v.getMaxMemory())) a += "m";
            res.add(a);
        }

        if (!StrUtils.isBlank(v.getPermSize()) && !v.isNoJVMArgs())
            if (jv != null && jv.ver != null && (jv.ver.startsWith("1.8") || jv.ver.startsWith("1.9"))); else res.add("-XX:MaxPermSize=" + v.getPermSize() + "m");

        if (!v.isNoJVMArgs())
            appendJVMArgs(res);

        HMCLog.log("On making java.library.path.");

        res.add("-Djava.library.path=" + provider.getDecompressNativesToLocation().getPath());//v.getSelectedMinecraftVersion().getNatives(v.getCanonicalGameDirFile()));
        res.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
        res.add("-Dfml.ignorePatchDiscrepancies=true");

        if (OS.os() != OS.WINDOWS)
            res.add("-Duser.home=" + gameDir.getParent());

        if (!v.isCanceledWrapper()) {
            res.add("-cp");
            res.add(StrUtils.parseParams("", Utils.getURL(), File.pathSeparator));
            res.add(Launcher.class.getCanonicalName());
        }
    }

    public List<String> makeLaunchingCommand() {
        HMCLog.log("*** Make shell command ***");

        ArrayList<String> res = new ArrayList<>();

        makeHeadCommand(res);
        makeSelf(res);

        HMCLog.log("On making launcher args.");

        if (StrUtils.isNotBlank(v.getHeight()) && StrUtils.isNotBlank(v.getWidth())) {
            res.add("--height");
            res.add(v.getHeight());
            res.add("--width");
            res.add(v.getWidth());
        }

        if (StrUtils.isNotBlank(v.getServerIp())) {
            String[] args = v.getServerIp().split(":");
            res.add("--server");
            res.add(args[0]);
            res.add("--port");
            res.add(args.length > 1 ? args[1] : "25565");
        }

        if (v.isFullscreen())
            res.add("--fullscreen");

        if (v.isDebug() && !v.isCanceledWrapper())
            res.add("-debug");

        if (StrUtils.isNotBlank(v.getMinecraftArgs()))
            res.addAll(Arrays.asList(v.getMinecraftArgs().split(" ")));

        return res;
    }

    /**
     * You must do these things:<br />
     * 1 minecraft class path<br />
     * 2 main class<br />
     * 3 minecraft arguments<br />
     *
     * @param list the command list you shoud edit.
     */
    protected abstract void makeSelf(List<String> list);

    protected void appendJVMArgs(List<String> list) {
    }

    public Profile getUserVersion() {
        return v;
    }
}
