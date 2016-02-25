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
import java.util.HashSet;
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
import org.jackhuang.hellominecraft.util.func.Function;
import org.jackhuang.hellominecraft.util.system.FileUtils;
import org.jackhuang.hellominecraft.util.tasks.DoingDoneListener;
import org.jackhuang.hellominecraft.util.tasks.Task;
import org.jackhuang.hellominecraft.util.tasks.TaskInfo;

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
            for (IMinecraftLibrary l : v.libraries)
                if (l.allow()) {
                    File ff = l.getFilePath(service.baseDirectory());
                    if (!ff.exists()) {
                        String libURL = service.getDownloadType().getProvider().getParsedLibraryDownloadURL(l.getDownloadInfo().getUrl(service.getDownloadType()));
                        if (libURL != null)
                            downloadLibraries.add(new DownloadLibraryJob(l, libURL, ff));
                    }
                }
        return downloadLibraries;
    }

    @Override
    public Task downloadMinecraft(String id) {
        return new TaskInfo("Download Minecraft") {
            @Override
            public void executeTask() throws Throwable {
                List<MinecraftRemoteVersion> versions = MinecraftRemoteVersions.getRemoteVersions(service.getDownloadType()).justDo();
                MinecraftRemoteVersion currentVersion = null;
                for (MinecraftRemoteVersion v : versions)
                    if (id.equals(v.id)) {
                        currentVersion = v;
                        break;
                    }
                if (currentVersion == null)
                    throw new RuntimeException("Cannot find version: " + id + " in remote repository.");
                String jsonURL = currentVersion.getUrl(service.getDownloadType());
                File vpath = new File(service.baseDirectory(), "versions/" + id);
                File mvt = new File(vpath, id + ".json");
                File mvj = new File(vpath, id + ".jar");
                if (!vpath.exists() && !vpath.mkdirs())
                    HMCLog.warn("Failed to make directories: " + vpath);
                if (mvt.exists() && !mvt.delete())
                    HMCLog.warn("Failed to delete " + mvt);
                if (mvj.exists() && !mvj.delete())
                    HMCLog.warn("Failed to delete " + mvj);

                Task t = new FileDownloadTask(jsonURL, mvt).setTag(id + ".json");
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
                        GameDownloadInfo i = mv.getClientDownloadInfo();
                        taskCollection.add(new FileDownloadTask(i.getUrl(service.getDownloadType()), mvj, i.sha1)
                            .setFailedCallbackReturnsNewURL(new DownloadTypeSwitcher(id + "/" + id + ".jar")).setTag(id + ".jar"));
                    }

                    @Override
                    public void onDoing(Task k, Collection<Task> taskCollection) {
                    }

                    @Override
                    public void onFailed(Task k) {
                        FileUtils.deleteDirectoryQuietly(vpath);
                    }
                });

                afters.add(t);
            }

            Collection<Task> afters = new HashSet<>();

            @Override
            public Collection<Task> getAfterTasks() {
                return afters;
            }
        };
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
            .append(new FileDownloadTask(vurl + id + ".jar", mvt).setFailedCallbackReturnsNewURL(new DownloadTypeSwitcher(id + "/" + id + ".jar")).setTag(id + ".jar"))
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

    private static class DownloadTypeSwitcher implements Function<Integer, String> {

        String suffix;

        public DownloadTypeSwitcher(String suffix) {
            this.suffix = suffix;
        }

        @Override
        public String apply(Integer t) {
            return DownloadType.values()[t / 2].getProvider().getVersionsDownloadURL() + suffix;
        }

    }

    @Override
    public Task downloadMinecraftJarTo(MinecraftVersion mv, File mvt) {
        String jar = mv.jar == null ? mv.id : mv.jar;
        GameDownloadInfo i = mv.getClientDownloadInfo();
        return new FileDownloadTask(i.getUrl(service.getDownloadType(), true), mvt, i.sha1).setTag(jar + ".jar");
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
}
