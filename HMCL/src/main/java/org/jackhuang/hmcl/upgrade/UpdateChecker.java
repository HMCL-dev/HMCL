/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.upgrade;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.*;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.IOException;
import java.util.LinkedHashMap;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.Lang.*;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class UpdateChecker {
    private UpdateChecker() {
    }

    private static final ObjectProperty<RemoteVersion> latestVersion = new SimpleObjectProperty<>();
    private static final ReadOnlyBooleanWrapper outdated = new ReadOnlyBooleanWrapper(false);
    private static final IntegerProperty runningThreads = new SimpleIntegerProperty(0);
    private static final BooleanBinding checkingUpdate = Bindings.createBooleanBinding(() -> runningThreads.get() > 0, runningThreads);

    private static UpdateTarget desiredTarget = null;

    public static void init() {
        requestCheckUpdate(UpdateChannel.getChannel(), settings().acceptPreviewUpdateProperty().get(), settings().autoDownloadUpdateProperty().get());
    }

    public static RemoteVersion getLatestVersion() {
        return latestVersion.get();
    }

    public static ReadOnlyObjectProperty<RemoteVersion> latestVersionProperty() {
        return latestVersion;
    }

    public static boolean isOutdated() {
        return outdated.get();
    }

    public static ReadOnlyBooleanProperty outdatedProperty() {
        return outdated;
    }

    public static boolean isCheckingUpdate() {
        return checkingUpdate.get();
    }

    public static BooleanBinding checkingUpdateProperty() {
        return checkingUpdate;
    }

    private static RemoteVersion checkUpdate(UpdateChannel channel, boolean preview) throws IOException {
        if (!IntegrityChecker.DISABLE_SELF_INTEGRITY_CHECK && !IntegrityChecker.isSelfVerified()) {
            throw new IOException("Self verification failed");
        }

        var query = new LinkedHashMap<String, String>();
        query.put("version", Metadata.VERSION);
        query.put("channel", preview ? channel.channelName + "-preview" : channel.channelName);

        String url = NetworkUtils.withQuery(Metadata.HMCL_UPDATE_URL, query);
        return RemoteVersion.fetch(channel, preview, url);
    }

    private static boolean isDevelopmentVersion(String version) {
        return version.contains("@") || // eg. @develop@
                version.contains("SNAPSHOT"); // eg. 3.5.SNAPSHOT
    }

    private static boolean checkOutdated(RemoteVersion latest) {
        if (latest == null || isDevelopmentVersion(Metadata.VERSION)) {
            return false;
        } else if (latest.force()
                || Metadata.isNightly()
                || latest.channel() == UpdateChannel.NIGHTLY
                || latest.channel() != UpdateChannel.getChannel()) {
            return !latest.version().equals(Metadata.VERSION);
        } else {
            return VersionNumber.compare(Metadata.VERSION, latest.version()) < 0;
        }
    }

    public static void requestCheckUpdate(UpdateChannel channel, boolean preview, boolean download) {
        requestCheckUpdate(new UpdateTarget(channel, preview, download));
    }

    private static void requestCheckUpdate(UpdateTarget target) {
        Platform.runLater(() -> {
            if (isCheckingUpdate() && target.concealedBy(desiredTarget))
                return;
            runningThreads.set(runningThreads.get() + 1);
            desiredTarget = target;

            thread(() -> {
                RemoteVersion result = null;
                boolean b = false;
                try {
                    result = checkUpdate(target.channel(), target.preview());
                    b = checkOutdated(result);
                    LOG.info("Latest version (" + target.channel() + ", preview=" + target.preview() + ") is " + result);
                    if (target.download() && b) result.tryDownload();
                } catch (Throwable e) {
                    LOG.warning("Failed to check for update", e);
                }
                boolean isOutdated = b;

                RemoteVersion finalResult = result;
                Platform.runLater(() -> {
                    if (finalResult != null && target.concealedBy(desiredTarget)) {
                        latestVersion.set(finalResult);
                        outdated.set(isOutdated);
                    }
                    runningThreads.set(runningThreads.get() - 1);
                });
            }, "Update Checker", true);
        });
    }

    private record UpdateTarget(UpdateChannel channel, boolean preview, boolean download) {
        public boolean concealedBy(UpdateTarget other) {
            if (other == null) return false;
            return other.channel == channel && other.preview == preview && (!download || other.download);
        }
    }
}
