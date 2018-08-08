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

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.VersionNumber.asVersion;

import java.io.IOException;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.NetworkUtils;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;

public final class UpdateChecker {
    private UpdateChecker() {}

    public static final String CHANNEL_STABLE = "stable";
    public static final String CHANNEL_DEV = "dev";

    private static StringProperty updateChannel = new SimpleStringProperty(CHANNEL_STABLE);

    private static ObjectProperty<RemoteVersion> latestVersion = new SimpleObjectProperty<>();
    private static BooleanBinding outdated = Bindings.createBooleanBinding(
            () -> {
                RemoteVersion latest = latestVersion.get();
                if (latest == null || isDevelopmentVersion(Metadata.VERSION)) {
                    return false;
                } else {
                    return asVersion(latest.getVersion()).compareTo(asVersion(Metadata.VERSION)) > 0;
                }
            },
            latestVersion);

    public static String getUpdateChannel() {
        return updateChannel.get();
    }

    public static void setUpdateChannel(String updateChannel) {
        UpdateChecker.updateChannel.set(updateChannel);
    }

    public static StringProperty updateChannelProperty() {
        return updateChannel;
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

    public static ObservableBooleanValue outdatedProperty() {
        return outdated;
    }

    public static void checkUpdate() throws IOException {
        if (!IntegrityChecker.isSelfVerified()) {
            return;
        }

        String channel = getUpdateChannel();
        String url = NetworkUtils.withQuery(Metadata.UPDATE_URL, mapOf(
                pair("version", Metadata.VERSION),
                pair("channel", channel)));

        RemoteVersion fetched = RemoteVersion.fetch(url);
        Platform.runLater(() -> {
            if (channel.equals(getUpdateChannel())) {
                latestVersion.set(fetched);
            }
        });
    }

    private static boolean isDevelopmentVersion(String version) {
        return version.contains("@") || // eg. @develop@
                version.contains("SNAPSHOT"); // eg. 3.1.SNAPSHOT
    }
}
