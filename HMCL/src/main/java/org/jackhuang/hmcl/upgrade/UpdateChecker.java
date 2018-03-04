/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.upgrade;

import com.google.gson.JsonSyntaxException;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.OutOfDateEvent;
import org.jackhuang.hmcl.task.TaskResult;
import org.jackhuang.hmcl.ui.construct.MessageBox;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.NetworkUtils;
import org.jackhuang.hmcl.util.VersionNumber;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;

/**
 *
 * @author huangyuhui
 */
public final class UpdateChecker {

    private volatile boolean outOfDate = false;
    private final VersionNumber base;
    private String versionString;
    private Map<String, String> download_link = null;

    public UpdateChecker(VersionNumber base) {
        this.base = base;
    }

    private VersionNumber value;

    public boolean isOutOfDate() {
        return outOfDate;
    }

    /**
     * Download the version number synchronously. When you execute this method
     * first, should leave "showMessage" false.
     *
     * @param showMessage If it is requested to warn the user that there is a
     *                    new version.
     *
     * @return the process observable.
     */
    public TaskResult<VersionNumber> process(final boolean showMessage) {
        return new TaskResult<VersionNumber>() {
            @Override
            public void execute() throws Exception {
                if (Launcher.VERSION.contains("@"))
                    return;

                if (value == null) {
                    versionString = NetworkUtils.doGet(NetworkUtils.toURL("http://huangyuhui.duapp.com/hmcl/update.php?version=" + Launcher.VERSION));
                    value = VersionNumber.asVersion(versionString);
                }

                if (value == null) {
                    Logging.LOG.warning("Unable to check update...");
                    if (showMessage)
                        MessageBox.show(Launcher.i18n("update.failed"));
                } else if (base.compareTo(value) < 0)
                    outOfDate = true;
                if (outOfDate)
                    setResult(value);
            }

            @Override
            public String getId() {
                return "update_checker.process";
            }
        };
    }

    /**
     * Get the <b>cached</b> newest version number, use "process" method to
     * download!
     *
     * @return the newest version number
     *
     * @see #process(boolean)
     */
    public VersionNumber getNewVersion() {
        return value;
    }

    /**
     * Get the download links.
     *
     * @return a JSON, which contains the server response.
     */
    public synchronized TaskResult<Map<String, String>> requestDownloadLink() {
        return new TaskResult<Map<String, String>>() {
            @Override
            public void execute() {
                if (download_link == null)
                    try {
                        download_link = Constants.GSON.<Map<String, String>>fromJson(NetworkUtils.doGet(NetworkUtils.toURL("http://huangyuhui.duapp.com/hmcl/update_link.php")), Map.class);
                    } catch (JsonSyntaxException | IOException e) {
                        Logging.LOG.log(Level.SEVERE, "Failed to get update link.", e);
                    }
                setResult(download_link);
            }

            @Override
            public String getId() {
                return "update_checker.request_download_link";
            }
        };
    }

    public static final String REQUEST_DOWNLOAD_LINK_ID = "update_checker.request_download_link";

    public void checkOutdate() {
        if (outOfDate)
            if (EventBus.EVENT_BUS.fireEvent(new OutOfDateEvent(this, getNewVersion())) != Event.Result.DENY) {
                Launcher.UPGRADER.download(this, getNewVersion());
            }
    }
}
