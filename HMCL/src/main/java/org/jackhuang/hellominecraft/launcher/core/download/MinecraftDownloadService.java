/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hellominecraft.launcher.core.download;

import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftDownloadService;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.launcher.core.GameException;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.launcher.core.version.GameDownloadInfo;
import org.jackhuang.hellominecraft.launcher.core.version.IMinecraftLibrary;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;
import org.jackhuang.hellominecraft.util.tasks.TaskWindow;
import org.jackhuang.hellominecraft.util.tasks.download.FileDownloadTask;
import org.jackhuang.hellominecraft.util.NetUtils;
import org.jackhuang.hellominecraft.util.OverridableSwingWorker;
import org.jackhuang.hellominecraft.util.system.FileUtils;
import org.jackhuang.hellominecraft.util.tasks.DoingDoneListener;
import org.jackhuang.hellominecraft.util.tasks.Task;
import org.jackhuang.hellominecraft.util.version.MinecraftRemoteVersion;
import org.jackhuang.hellominecraft.util.version.MinecraftRemoteVersions;

/**
 *
 * @author huangyuhui
 */
public class MinecraftDownloadService extends IMinecraftDownloadService {

    public MinecraftDownloadService(IMinecraftService service) {
        super(service);
    }

    @Override
    public List<DownloadLibraryJob> getDownloadLibraries(MinecraftVersion mv) throws GameException {
        ArrayList<DownloadLibraryJob> downloadLibraries = new ArrayList<>();
        if (mv == null)
            return downloadLibraries;
        MinecraftVersion v = mv.resolve(service.version());
        if (v.libraries != null)
            for (IMinecraftLibrary l : v.libraries) {
                l.init();
                if (l.allow()) {
                    File ff = l.getFilePath(service.baseDirectory());
                    if (!ff.exists()) {
                        String libURL = service.getDownloadType().getProvider().getLibraryDownloadURL() + "/";
                        libURL = service.getDownloadType().getProvider().getParsedLibraryDownloadURL(l.getDownloadURL(libURL, service.getDownloadType()));
                        if (libURL != null)
                            downloadLibraries.add(new DownloadLibraryJob(l.name, libURL, ff));
                    }
                }
            }
        return downloadLibraries;
    }

    @Override
    public MinecraftVersion downloadMinecraft(String id) {
        String vurl = service.getDownloadType().getProvider().getVersionsDownloadURL() + id + "/";
        File vpath = new File(service.baseDirectory(), "versions/" + id);
        File mvt = new File(vpath, id + ".json");
        File mvj = new File(vpath, id + ".jar");
        if (!vpath.exists() && !vpath.mkdirs())
            HMCLog.warn("Failed to make directories: " + vpath);
        if (mvt.exists() && !mvt.delete())
            HMCLog.warn("Failed to delete " + mvt);
        if (mvj.exists() && !mvj.delete())
            HMCLog.warn("Failed to delete " + mvj);

        Task t = new FileDownloadTask(vurl + id + ".json", mvt).setTag(id + ".json");
        t.addTaskListener(new DoingDoneListener<Task>() {
            @Override
            public void onDone(Task k, Collection<Task> taskCollection) {
                MinecraftVersion mv;
                try {
                    mv = C.GSON.fromJson(FileUtils.readFileToStringQuietly(mvt), MinecraftVersion.class);
                    if (mv == null)
                        throw new JsonSyntaxException("incorrect version");
                } catch (JsonSyntaxException ex) {
                    HMCLog.err("Failed to parse minecraft version json.", ex);
                    onFailed(k);
                    return;
                }
                String jarURL = vurl + id + ".jar", hash = null;
                if (mv != null && mv.downloads != null && service.getDownloadType().getProvider().isAllowedToUseSelfURL()) {
                    GameDownloadInfo gdi = mv.downloads.get("client");
                    if (gdi != null) {
                        if (gdi.url != null)
                            jarURL = gdi.url;
                        if (gdi.sha1 != null)
                            hash = gdi.sha1;
                    }
                }

                taskCollection.add(new FileDownloadTask(jarURL, mvj, hash).setTag(id + ".jar"));
            }

            @Override
            public void onDoing(Task k, Collection<Task> taskCollection) {
            }

            @Override
            public void onFailed(Task k) {
                FileUtils.deleteDirectoryQuietly(vpath);
            }
        });

        if (!TaskWindow.factory().append(t).create())
            return null;
        try {
            return C.GSON.fromJson(FileUtils.readFileToStringQuietly(mvt), MinecraftVersion.class);
        } catch (JsonSyntaxException ex) {
            HMCLog.err("Failed to parse minecraft version json.", ex);
            return null;
        }
    }

    @Override
    public boolean downloadMinecraftJar(String id) {
        String vurl = service.getDownloadType().getProvider().getVersionsDownloadURL() + id + "/";
        File vpath = new File(service.baseDirectory(), "versions/" + id);
        File mvv = new File(vpath, id + ".jar"), moved = null;
        if (mvv.exists()) {
            moved = new File(vpath, id + "-renamed.jar");
            if (!mvv.renameTo(moved))
                HMCLog.warn("Failed to rename " + mvv + " to " + moved);
        }
        File mvt = new File(vpath, id + ".jar");
        if (!vpath.exists() && !vpath.mkdirs())
            HMCLog.warn("Failed to make version folder " + vpath);
        if (TaskWindow.factory()
            .append(new FileDownloadTask(vurl + id + ".jar", mvt).setTag(id + ".jar"))
            .create()) {
            if (moved != null && moved.exists() && !moved.delete())
                HMCLog.warn("Failed to delete " + moved);
            return true;
        } else {
            if (mvt.exists() && !mvt.delete())
                HMCLog.warn("Failed to delete game jar " + mvt);
            if (moved != null && moved.exists() && !moved.renameTo(mvt))
                HMCLog.warn("Failed to rename " + moved + " to " + mvt);
            return false;
        }
    }

    @Override
    public Task downloadMinecraftJarTo(String id, File mvt) {
        String vurl = service.getDownloadType().getProvider().getVersionsDownloadURL() + id + "/";
        return new FileDownloadTask(vurl + id + ".jar", mvt).setTag(id + ".jar");
    }

    @Override
    public boolean downloadMinecraftVersionJson(String id) {
        String vurl = service.getDownloadType().getProvider().getVersionsDownloadURL() + id + "/";
        File vpath = new File(service.baseDirectory(), "versions/" + id);
        File mvv = new File(vpath, id + ".json"), moved = null;
        if (mvv.exists()) {
            moved = new File(vpath, id + "-renamed.json");
            if (!mvv.renameTo(moved))
                HMCLog.warn("Failed to rename " + mvv + " to " + moved);
        }
        File mvt = new File(vpath, id + ".json");
        if (!vpath.exists() && !vpath.mkdirs())
            HMCLog.warn("Failed to make version folder " + vpath);
        if (TaskWindow.factory()
            .append(new FileDownloadTask(vurl + id + ".json", mvt).setTag(id + ".json"))
            .create()) {
            if (moved != null && moved.exists() && !moved.delete())
                HMCLog.warn("Failed to delete " + moved);
            return true;
        } else {
            if (mvt.exists() && !mvt.delete())
                HMCLog.warn("Failed to delete minecraft version json" + mvt);
            if (moved != null && moved.exists() && !moved.renameTo(mvt))
                HMCLog.warn("Failed to rename " + moved + " to " + mvt);
            return false;
        }
    }

    @Override
    public OverridableSwingWorker<MinecraftRemoteVersion> getRemoteVersions() {
        return new OverridableSwingWorker<MinecraftRemoteVersion>() {
            @Override
            protected void work() throws Exception {
                MinecraftRemoteVersions r = C.GSON.fromJson(NetUtils.get(service.getDownloadType().getProvider().getVersionsListDownloadURL()), MinecraftRemoteVersions.class);
                if (r != null && r.versions != null)
                    publish(r.versions.toArray(new MinecraftRemoteVersion[r.versions.size()]));
            }
        };
    }
}
