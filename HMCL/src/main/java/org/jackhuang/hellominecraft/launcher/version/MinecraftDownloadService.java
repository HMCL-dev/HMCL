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
package org.jackhuang.hellominecraft.launcher.version;

import org.jackhuang.hellominecraft.launcher.launch.IMinecraftDownloadService;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.launch.GameLauncher;
import org.jackhuang.hellominecraft.launcher.settings.Profile;
import org.jackhuang.hellominecraft.tasks.TaskWindow;
import org.jackhuang.hellominecraft.tasks.download.FileDownloadTask;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.utils.system.FileUtils;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import org.jackhuang.hellominecraft.version.MinecraftRemoteVersion;
import org.jackhuang.hellominecraft.version.MinecraftRemoteVersions;
import rx.Observable;

/**
 *
 * @author huangyuhui
 */
public class MinecraftDownloadService extends IMinecraftDownloadService {

    MinecraftVersionManager mgr;

    public MinecraftDownloadService(Profile p, MinecraftVersionManager mgr) {
        super(p);
        this.mgr = mgr;
    }

    @Override
    public List<GameLauncher.DownloadLibraryJob> getDownloadLibraries() {
        ArrayList<GameLauncher.DownloadLibraryJob> downloadLibraries = new ArrayList<>();
        if (profile.getSelectedMinecraftVersion() == null)
            return downloadLibraries;
        MinecraftVersion v = profile.getSelectedMinecraftVersion().resolve(mgr);
        if (v.libraries != null)
            for (IMinecraftLibrary l : v.libraries) {
                l.init();
                if (l.allow()) {
                    File ff = l.getFilePath(profile.getCanonicalGameDirFile());
                    if (!ff.exists()) {
                        String libURL = profile.getDownloadType().getProvider().getLibraryDownloadURL() + "/";
                        libURL = profile.getDownloadType().getProvider().getParsedLibraryDownloadURL(l.getDownloadURL(libURL, profile.getDownloadType()));
                        if (libURL != null)
                            downloadLibraries.add(new GameLauncher.DownloadLibraryJob(l.name, libURL, ff));
                    }
                }
            }
        return downloadLibraries;
    }

    @Override
    public boolean install(String id) {
        MinecraftVersion v = downloadMinecraft(id);
        if (v != null) {
            mgr.versions.put(v.id, v);
            return true;
        }
        return false;
    }

    @Override
    public MinecraftVersion downloadMinecraft(String id) {
        String vurl = profile.getDownloadType().getProvider().getVersionsDownloadURL() + id + "/";
        File vpath = new File(profile.getCanonicalGameDirFile(), "versions/" + id);
        File mvt = new File(vpath, id + ".json");
        File mvj = new File(vpath, id + ".jar");
        vpath.mkdirs();
        mvt.delete();
        mvj.delete();

        if (TaskWindow.getInstance()
            .addTask(new FileDownloadTask(vurl + id + ".json", IOUtils.tryGetCanonicalFile(mvt)).setTag(id + ".json"))
            .addTask(new FileDownloadTask(vurl + id + ".jar", IOUtils.tryGetCanonicalFile(mvj)).setTag(id + ".jar"))
            .start())
            try {
                return C.gson.fromJson(FileUtils.readFileToStringQuietly(mvt), MinecraftVersion.class);
            } catch (JsonSyntaxException ex) {
                HMCLog.err("Failed to parse minecraft version json.", ex);
            }
        else
            FileUtils.deleteDirectoryQuietly(vpath);
        return null;
    }

    @Override
    public boolean downloadMinecraftJar(String id) {
        String vurl = profile.getDownloadType().getProvider().getVersionsDownloadURL() + id + "/";
        File vpath = new File(profile.getCanonicalGameDirFile(), "versions/" + id);
        File mvv = new File(vpath, id + ".jar"), moved = null;
        if (mvv.exists()) {
            moved = new File(vpath, id + "-renamed.jar");
            mvv.renameTo(moved);
        }
        File mvt = new File(vpath, id + ".jar");
        vpath.mkdirs();
        if (TaskWindow.getInstance()
            .addTask(new FileDownloadTask(vurl + id + ".jar", IOUtils.tryGetCanonicalFile(mvt)).setTag(id + ".jar"))
            .start()) {
            if (moved != null)
                moved.delete();
            return true;
        } else {
            mvt.delete();
            if (moved != null)
                moved.renameTo(mvt);
            return false;
        }
    }

    @Override
    public boolean downloadMinecraftVersionJson(String id) {
        String vurl = profile.getDownloadType().getProvider().getVersionsDownloadURL() + id + "/";
        File vpath = new File(profile.getCanonicalGameDirFile(), "versions/" + id);
        File mvv = new File(vpath, id + ".json"), moved = null;
        if (mvv.exists()) {
            moved = new File(vpath, id + "-renamed.json");
            mvv.renameTo(moved);
        }
        File mvt = new File(vpath, id + ".json");
        vpath.mkdirs();
        if (TaskWindow.getInstance()
            .addTask(new FileDownloadTask(vurl + id + ".json", IOUtils.tryGetCanonicalFile(mvt)).setTag(id + ".json"))
            .start()) {
            if (moved != null)
                moved.delete();
            return true;
        } else {
            mvt.delete();
            if (moved != null)
                moved.renameTo(mvt);
            return false;
        }
    }

    @Override
    public Observable<MinecraftRemoteVersion> getRemoteVersions() {
        return NetUtils.getRx(profile.getDownloadType().getProvider().getVersionsListDownloadURL())
            .map(r -> C.gson.fromJson(r, MinecraftRemoteVersions.class))
            .filter(r -> r != null && r.versions != null)
            .flatMap(r -> Observable.from(r.versions));
    }
}
