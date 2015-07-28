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
package org.jackhuang.hellominecraft.launcher.utils.assets;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.launch.IMinecraftProvider;
import org.jackhuang.hellominecraft.launcher.utils.download.IDownloadProvider;
import org.jackhuang.hellominecraft.launcher.version.MinecraftVersion;
import org.jackhuang.hellominecraft.tasks.Task;
import org.jackhuang.hellominecraft.tasks.download.FileDownloadTask;
import org.jackhuang.hellominecraft.utils.functions.Consumer;
import org.jackhuang.hellominecraft.utils.code.DigestUtils;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import org.jackhuang.hellominecraft.utils.NetUtils;

/**
 * Assets
 *
 * @author huangyuhui
 */
public abstract class IAssetsHandler {

    protected ArrayList<String> assetsDownloadURLs;
    protected ArrayList<File> assetsLocalNames;
    protected final String name;
    protected List<Contents> contents;

    public IAssetsHandler(String name) {
        this.name = name;
    }

    private static final List<IAssetsHandler> assetsHandlers = new ArrayList<>();

    public static IAssetsHandler getAssetsHandler(int i) {
        return assetsHandlers.get(i);
    }

    public static List<IAssetsHandler> getAssetsHandlers() {
        return assetsHandlers;
    }

    static {
        assetsHandlers.add(new AssetsMojangLoader(C.i18n("assets.list.1_7_3_after")));
    }

    /**
     * interface name
     *
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * All the files assets needed
     *
     * @param x finished event
     */
    public abstract void getList(Consumer<String[]> x);

    /**
     * Will be invoked when the user invoked "Download all assets".
     *
     * @param sourceType Download Source
     * @return Download File Task
     */
    public abstract Task getDownloadTask(IDownloadProvider sourceType);

    /**
     * assets path
     */
    protected MinecraftVersion mv;
    protected IMinecraftProvider mp;

    /**
     * @param mp
     * @param mv
     */
    public void setAssets(IMinecraftProvider mp, MinecraftVersion mv) {
        this.mp = mp;
        this.mv = mv;
    }

    public abstract boolean isVersionAllowed(String formattedVersion);

    protected class AssetsTask extends Task {

        ArrayList<Task> al;
        String u;
        int progress, max;

        public AssetsTask(String url) {
            this.u = url;
        }

        @Override
        public boolean executeTask() {
            if (mv == null || assetsDownloadURLs == null) {
                setFailReason(new RuntimeException(C.i18n("assets.not_refreshed")));
                return false;
            }
            progress = 0;
            max = assetsDownloadURLs.size();
            al = new ArrayList<>();
            int hasDownloaded = 0;
            for (int i = 0; i < max; i++) {
                String mark = assetsDownloadURLs.get(i);
                String url = u + mark;
                File location = assetsLocalNames.get(i);
                if (!location.getParentFile().exists()) location.getParentFile().mkdirs();
                if (location.isDirectory()) continue;
                boolean need = true;
                try {
                    if (location.exists()) {
                        FileInputStream fis = new FileInputStream(location);
                        String sha = DigestUtils.sha1Hex(NetUtils.getBytesFromStream(fis));
                        IOUtils.closeQuietly(fis);
                        if (contents.get(i).eTag.equals(sha)) {
                            hasDownloaded++;
                            HMCLog.log("File " + assetsLocalNames.get(i) + " has downloaded successfully, skipped downloading.");
                            if (ppl != null)
                                ppl.setProgress(this, hasDownloaded, max);
                            continue;
                        }
                    }
                } catch (IOException e) {
                    HMCLog.warn("Failed to get hash: " + location, e);
                    need = !location.exists();
                }
                if (need) al.add(new FileDownloadTask(url, location).setTag(mark));
            }
            return true;
        }

        @Override
        public Collection<Task> getAfterTasks() {
            return al;
        }

        @Override
        public String getInfo() {
            return C.i18n("assets.download");
        }
    }
}
