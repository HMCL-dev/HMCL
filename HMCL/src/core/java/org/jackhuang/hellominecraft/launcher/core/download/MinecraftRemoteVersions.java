/*
 * Hello Minecraft!.
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

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.net.NetUtils;
import org.jackhuang.hellominecraft.util.AbstractSwingWorker;

/**
 *
 * @author huangyuhui
 */
public class MinecraftRemoteVersions {

    @SerializedName("versions")
    public ArrayList<MinecraftRemoteVersion> versions;
    @SerializedName("latest")
    public MinecraftRemoteLatestVersion latest;

    private static volatile MinecraftRemoteVersions INSTANCE = null;
    private static final Object INSTANCE_LOCK = new Object();

    public static RemoteVersionsTask getRemoteVersions(DownloadType type) {
        return new RemoteVersionsTask(type) {
            @Override
            public void work() throws Exception {
                synchronized (INSTANCE_LOCK) {
                    if (INSTANCE != null)
                        send(INSTANCE.versions.toArray(new MinecraftRemoteVersion[INSTANCE.versions.size()]));
                    else
                        super.work();
                }
            }
        };
    }

    public static RemoteVersionsTask refreshRomoteVersions(DownloadType type) {
        return new RemoteVersionsTask(type);
    }

    public static class RemoteVersionsTask extends AbstractSwingWorker<MinecraftRemoteVersion> {

        DownloadType type;

        public RemoteVersionsTask(DownloadType type) {
            this.type = type;
        }

        @Override
        public void work() throws Exception {
            MinecraftRemoteVersions r = C.GSON.fromJson(NetUtils.get(type.getProvider().getVersionsListDownloadURL()), MinecraftRemoteVersions.class);
            if (r != null && r.versions != null) {
                INSTANCE = r;
                send(r.versions.toArray(new MinecraftRemoteVersion[r.versions.size()]));
            }
        }

    }

}
