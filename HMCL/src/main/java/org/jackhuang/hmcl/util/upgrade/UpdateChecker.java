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
package org.jackhuang.hmcl.util.upgrade;

import org.jackhuang.hmcl.util.ui.MessageBox;
import org.jackhuang.hmcl.api.VersionNumber;
import org.jackhuang.hmcl.api.event.EventHandler;
import org.jackhuang.hmcl.util.net.NetUtils;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import org.jackhuang.hmcl.api.HMCLog;
import java.util.Map;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.api.HMCLApi;
import org.jackhuang.hmcl.api.event.SimpleEvent;
import org.jackhuang.hmcl.api.event.OutOfDateEvent;
import org.jackhuang.hmcl.util.AbstractSwingWorker;
import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.util.IUpdateChecker;

/**
 *
 * @author huangyuhui
 */
public final class UpdateChecker implements IUpdateChecker {

    private volatile boolean outOfDate = false;
    public VersionNumber base;
    public String versionString;
    public String type;
    private Map<String, String> download_link = null;

    public UpdateChecker(VersionNumber base, String type) {
        this.base = base;
        this.type = type;
    }

    VersionNumber value;

    public boolean isOutOfDate() {
        return outOfDate;
    }

    @Override
    public AbstractSwingWorker<VersionNumber> process(final boolean showMessage) {
        return new AbstractSwingWorker<VersionNumber>() {
            @Override
            protected void work() throws Exception {
                if (value == null) {
                    versionString = NetUtils.get("http://hmcl.huangyuhui.net/api/update?version=" + Main.LAUNCHER_VERSION);
                    value = VersionNumber.asVersion(versionString);
                }

                if (value == null) {
                    HMCLog.warn("Failed to check update...");
                    if (showMessage)
                        MessageBox.show(C.i18n("update.failed"));
                } else if (base.compareTo(value) < 0)
                    outOfDate = true;
                if (outOfDate)
                    publish(value);
            }
        };
    }

    @Override
    public VersionNumber getNewVersion() {
        return value;
    }

    @Override
    public synchronized AbstractSwingWorker<Map<String, String>> requestDownloadLink() {
        return new AbstractSwingWorker<Map<String, String>>() {
            @Override
            protected void work() throws Exception {
                if (download_link == null)
                    try {
                        download_link = C.GSON.<Map<String, String>>fromJson(NetUtils.get("http://hmcl.huangyuhui.net/api/update_link?version=" + Main.LAUNCHER_VERSION), Map.class);
                    } catch (JsonSyntaxException | IOException e) {
                        HMCLog.warn("Failed to get update link.", e);
                    }
                publish(download_link);
            }
        };
    }

    public final EventHandler<SimpleEvent<VersionNumber>> upgrade = new EventHandler<>();

    @Override
    public void checkOutdate() {
        if (outOfDate)
            if (HMCLApi.EVENT_BUS.fireChannelResulted(new OutOfDateEvent(this, getNewVersion())))
                upgrade.fire(new SimpleEvent<>(this, getNewVersion()));
    }
}
