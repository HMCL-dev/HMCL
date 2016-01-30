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
package org.jackhuang.hellominecraft.util;

import org.jackhuang.hellominecraft.util.logging.HMCLog;
import java.util.Map;

/**
 *
 * @author huangyuhui
 */
public final class UpdateChecker implements IUpdateChecker {

    public boolean OUT_DATED = false;
    public VersionNumber base;
    public String versionString;
    public String type;
    private Map<String, String> download_link = null;

    public UpdateChecker(VersionNumber base, String type) {
        this.base = base;
        this.type = type;
    }

    VersionNumber value;

    @Override
    public OverridableSwingWorker<VersionNumber> process(final boolean showMessage) {
        return new OverridableSwingWorker() {
            @Override
            protected void work() throws Exception {

                if (value == null) {
                    versionString = NetUtils.get("http://huangyuhui.duapp.com/info.php?type=" + type);
                    value = VersionNumber.check(versionString);
                }

                if (value == null) {
                    HMCLog.warn("Failed to check update...");
                    if (showMessage)
                        MessageBox.Show(C.i18n("update.failed"));
                } else if (VersionNumber.isOlder(base, value))
                    OUT_DATED = true;
                if (OUT_DATED)
                    publish(value);
            }
        };
    }

    @Override
    public VersionNumber getNewVersion() {
        return value;
    }

    @Override
    public synchronized OverridableSwingWorker<Map<String, String>> requestDownloadLink() {
        return new OverridableSwingWorker() {
            @Override
            protected void work() throws Exception {
                if (download_link == null)
                    try {
                        download_link = C.GSON.fromJson(NetUtils.get("http://huangyuhui.duapp.com/update_link.php?type=" + type), Map.class);
                    } catch (Exception e) {
                        HMCLog.warn("Failed to get update link.", e);
                    }
                publish(download_link);
            }
        };
    }

    public final EventHandler<VersionNumber> outdated = new EventHandler<>(this);

    @Override
    public void checkOutdate() {
        if (OUT_DATED)
            outdated.execute(getNewVersion());
    }
}
