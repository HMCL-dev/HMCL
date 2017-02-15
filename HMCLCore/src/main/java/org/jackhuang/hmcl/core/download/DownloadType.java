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

import org.jackhuang.hmcl.api.HMCLApi;
import org.jackhuang.hmcl.api.event.config.DownloadTypeChangedEvent;
import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.util.task.TaskWindow;

/**
 *
 * @author huangyuhui
 */
public enum DownloadType {

    Mojang("download.mojang", new MojangDownloadProvider()),
    BMCL("download.BMCL", new BMCLAPIDownloadProvider()),
    Curse("Curse CDN", new CurseDownloadProvider());

    private final String name;
    private final IDownloadProvider provider;

    DownloadType(String a, IDownloadProvider provider) {
        name = a;
        this.provider = provider;
    }

    public IDownloadProvider getProvider() {
        return provider;
    }

    public String getName() {
        return C.i18n(name);
    }

    private static DownloadType suggestedDownloadType = Mojang;

    public static DownloadType getSuggestedDownloadType() {
        return suggestedDownloadType;
    }

    public static void setSuggestedDownloadType(String id) {
        DownloadType s = DownloadType.valueOf(id);
        if (s == null)
            throw new IllegalArgumentException("download type should not be null.");
        TaskWindow.downloadSource = s.getName();
        DownloadType.suggestedDownloadType = s;
    }

    static {
        HMCLApi.EVENT_BUS.channel(DownloadTypeChangedEvent.class).register(t -> setSuggestedDownloadType(t.getValue()));
    }
}
