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
package org.jackhuang.hellominecraft.launcher.core.launch;

import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftLoader;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.Launcher;
import org.jackhuang.hellominecraft.launcher.core.GameException;
import org.jackhuang.hellominecraft.launcher.core.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;
import org.jackhuang.hellominecraft.utils.system.JdkVersion;
import org.jackhuang.hellominecraft.utils.MathUtils;
import org.jackhuang.hellominecraft.utils.MessageBox;
import org.jackhuang.hellominecraft.utils.system.OS;
import org.jackhuang.hellominecraft.utils.system.Platform;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.utils.Utils;

/**
 *
 * @author huangyuhui
 */
public abstract class AbstractMinecraftLoader implements IMinecraftLoader {

    protected LaunchOptions options;
    protected UserProfileProvider lr;
    protected File gameDir;
    protected IMinecraftService service;
    protected final MinecraftVersion version;

    public AbstractMinecraftLoader(LaunchOptions options, IMinecraftService service, String versionId, UserProfileProvider lr) throws GameException {
        this.lr = lr;

        this.options = options;
        this.service = service;
        this.gameDir = service.baseDirectory();
        this.version = service.version().getVersionById(versionId).resolve(service.version());
    }

    @Override
    public MinecraftVersion getMinecraftVersion() {
        return version;
    }

    public void makeHeadCommand(List<String> res) {
        HMCLog.log("On making head command.");

        JdkVersion jv = options.getJava();
        res.add(options.getJavaDir());

        if (options.hasJavaArgs())
            res.addAll(Arrays.asList(StrUtils.tokenize(options.getJavaArgs())));

        if (!options.isNoJVMArgs()) {
            appendJVMArgs(res);

            if (jv == null || !jv.isEarlyAccess()) {
                if (OS.os() == OS.WINDOWS)
                    res.add("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump");
                res.add("-XX:+UseConcMarkSweepGC");
                res.add("-XX:+CMSIncrementalMode");
                res.add("-XX:-UseAdaptiveSizePolicy");
                res.add("-XX:-OmitStackTraceInFastThrow");

                res.add("-Xmn128m");
            }
            if (!StrUtils.isBlank(options.getPermSize()))
                if (jv == null || jv.getParsedVersion() < JdkVersion.JAVA_18)
                    res.add("-XX:PermSize=" + options.getPermSize() + "m");
                else if (jv.getParsedVersion() >= JdkVersion.JAVA_18)
                    res.add("-XX:MetaspaceSize=" + options.getPermSize() + "m");
        }

        if (jv != null) {
            HMCLog.log("Java Version: " + jv.getVersion());
            HMCLog.log("Java Platform: " + jv.getPlatform().getBit());
        }
        HMCLog.log("System Platform: " + Platform.getPlatform().getBit());

        if (jv != null && jv.getPlatform() == Platform.BIT_32 && Platform.getPlatform() == Platform.BIT_64)
            MessageBox.Show(C.i18n("advice.os64butjdk32"));

        if (!StrUtils.isBlank(options.getMaxMemory())) {
            int mem = MathUtils.parseMemory(options.getMaxMemory(), 2147483647);
            if (jv != null && jv.getPlatform() == Platform.BIT_32 && mem > 1024)
                MessageBox.Show(C.i18n("launch.too_big_memory_alloc_64bit"));
            else {
                long a = OS.getTotalPhysicalMemory() / 1024 / 1024;
                HMCLog.log("System Physical Memory: " + a);
                if (a > 0 && a < mem)
                    MessageBox.Show(C.i18n("launch.too_big_memory_alloc_free_space_too_low", a));
            }
            String a = "-Xmx" + options.getMaxMemory();
            if (MathUtils.canParseInt(options.getMaxMemory()))
                a += "m";
            res.add(a);
        }

        res.add("-Djava.library.path=" + service.version().getDecompressNativesToLocation(version).getPath());
        res.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
        res.add("-Dfml.ignorePatchDiscrepancies=true");

        if (OS.os() != OS.WINDOWS)
            res.add("-Duser.home=" + gameDir.getParent());

        if (!options.isCanceledWrapper()) {
            res.add("-cp");
            res.add(StrUtils.parseParams("", Utils.getURL(), File.pathSeparator));
            res.add(Launcher.class.getCanonicalName());
        }
    }

    @Override
    public List<String> makeLaunchingCommand() {
        HMCLog.log("*** Make shell command ***");

        ArrayList<String> res = new ArrayList<>();

        makeHeadCommand(res);
        makeSelf(res);

        HMCLog.log("On making launcher args.");

        if (StrUtils.isNotBlank(options.getHeight()) && StrUtils.isNotBlank(options.getWidth())) {
            res.add("--height");
            res.add(options.getHeight());
            res.add("--width");
            res.add(options.getWidth());
        }

        String serverIp = options.getServerIp();
        if (lr.getServer() != null)
            serverIp = lr.getServer().addr;
        if (StrUtils.isNotBlank(serverIp)) {
            String[] args = serverIp.split(":");
            res.add("--server");
            res.add(args[0]);
            res.add("--port");
            res.add(args.length > 1 ? args[1] : "25565");
        }

        if (options.isFullscreen())
            res.add("--fullscreen");

        if (options.isDebug() && !options.isCanceledWrapper())
            res.add("-debug");

        if (StrUtils.isNotBlank(options.getProxyHost()) && StrUtils.isNotBlank(options.getProxyPort()) && MathUtils.canParseInt(options.getProxyPort())) {
            res.add("-proxyHost=" + options.getProxyHost());
            res.add("-proxyPort=" + options.getProxyPort());
            if (StrUtils.isNotBlank(options.getProxyUser()) && StrUtils.isNotBlank(options.getProxyPass())) {
                res.add("-proxyUsername=" + options.getProxyUser());
                res.add("-proxyPassword=" + options.getProxyPass());
            }
        }

        if (StrUtils.isNotBlank(options.getMinecraftArgs()))
            res.addAll(Arrays.asList(options.getMinecraftArgs().split(" ")));

        return res;
    }

    /**
     * You must do these things:
     * <ul>
     * <li>minecraft class path</li>
     * <li>main class</li>
     * <li>minecraft arguments</li>
     * </ul>
     *
     * @param list the command list you shoud edit.
     */
    protected abstract void makeSelf(List<String> list);

    protected void appendJVMArgs(List<String> list) {
    }
}
