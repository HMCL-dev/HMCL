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
package org.jackhuang.hmcl.core.download;

import org.jackhuang.hmcl.core.service.IMinecraftDownloadService;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.core.GameException;
import org.jackhuang.hmcl.core.service.IMinecraftService;
import org.jackhuang.hmcl.core.version.GameDownloadInfo;
import org.jackhuang.hmcl.api.game.IMinecraftLibrary;
import org.jackhuang.hmcl.core.version.MinecraftVersion;
import org.jackhuang.hmcl.util.net.FileDownloadTask;
import org.jackhuang.hmcl.api.func.Function;
import org.jackhuang.hmcl.util.sys.FileUtils;
import org.jackhuang.hmcl.util.task.Task;
import org.jackhuang.hmcl.util.task.TaskInfo;

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
        for (IMinecraftLibrary l : v.getLibraries())
            if (l != null && l.allow()) {
                File ff = service.version().getLibraryFile(mv, l);
                if (!ff.exists() && l.getDownloadURL(service.getDownloadType().name()) != null) {
                    String libURL = l.getDownloadURL(service.getDownloadType().name());
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
            public Collection<Task> getDependTasks() {
                return Arrays.asList(downloadMinecraftVersionJson(id));
            }

            @Override
            public void executeTask(boolean areDependTasksSucceeded) throws Throwable {
                File vpath = new File(service.baseDirectory(), "versions/" + id);
                if (!areDependTasksSucceeded) {
                    FileUtils.deleteDirectory(vpath);
                    throw new RuntimeException("Cannot continue because of download failing.");
                }
                File mvj = new File(vpath, id + ".jar");
                if (mvj.exists() && !mvj.delete())
                    HMCLog.warn("Failed to delete " + mvj);
                try {
                    MinecraftVersion mv = C.GSON.fromJson(FileUtils.readQuietly(new File(vpath, id + ".json")), MinecraftVersion.class);
                    if (mv == null)
                        throw new JsonSyntaxException("incorrect version");

                    afters.add(downloadMinecraftJar(mv, mvj));
                } catch (JsonSyntaxException ex) {
                    HMCLog.err("Failed to parse minecraft version json.", ex);
                    FileUtils.deleteDirectory(vpath);
                }
            }

            Collection<Task> afters = new HashSet<>();

            @Override
            public Collection<Task> getAfterTasks() {
                return afters;
            }
        };
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
    public Task downloadMinecraftJar(MinecraftVersion mv, File mvj) {
        GameDownloadInfo i = mv.getClientDownloadInfo();
        return new FileDownloadTask(i.getUrl(service.getDownloadType()), mvj, i.sha1)
            .setFailedCallbackReturnsNewURL(new DownloadTypeSwitcher(mv.id + "/" + mv.id + ".jar")).setTag(mv.id + ".jar");
    }

    @Override
    public Task downloadMinecraftVersionJson(String id) {
        return new TaskInfo("Download Minecraft Json") {
            @Override
            public void executeTask(boolean areDependTasksSucceeded) throws Throwable {
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
                if (!FileUtils.makeDirectory(vpath))
                    HMCLog.warn("Failed to make directories: " + vpath);
                if (mvt.exists() && !mvt.delete())
                    HMCLog.warn("Failed to delete " + mvt);

                afters.add(new FileDownloadTask(jsonURL, mvt).setTag(id + ".json"));
            }

            Collection<Task> afters = new HashSet<>();

            @Override
            public Collection<Task> getAfterTasks() {
                return afters;
            }
        };
    }
}
