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
import javafx.beans.value.ObservableBooleanValue;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.IOException;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.versioning.VersionNumber.asVersion;

public final class UpdateChecker {
    private UpdateChecker() {
    }

    private static ObjectProperty<RemoteVersion> latestVersion = new SimpleObjectProperty<>();
    private static BooleanBinding outdated = Bindings.createBooleanBinding(
            () -> {
                RemoteVersion latest = latestVersion.get();
                if (latest != null) {
                    return true;
                }
                if (latest == null || isDevelopmentVersion(Metadata.VERSION)) {
                    return false;
                } else {
                    // We can update from development version to stable version,
                    // which can be downgrading.
                    if (latest.getChannel() == UpdateChannel.NIGHTLY) {
                        return !latest.getVersion().equals(Metadata.VERSION);
                    } else {
                        return asVersion(latest.getVersion()).compareTo(asVersion(Metadata.VERSION)) != 0;
                    }
                }
            },
            latestVersion);
    private static ReadOnlyBooleanWrapper checkingUpdate = new ReadOnlyBooleanWrapper(false);

    public static void init() {
        requestCheckUpdate(UpdateChannel.getCustomUpdateChannel());
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

    public static boolean isCheckingUpdate() {
        return checkingUpdate.get();
    }

    public static ReadOnlyBooleanProperty checkingUpdateProperty() {
        return checkingUpdate.getReadOnlyProperty();
    }

    private static RemoteVersion checkUpdate(UpdateChannel channel) throws IOException {
        if ("true".equals(System.getProperty("hmcl.self_integrity_check.disable"))) {
            throw new IOException("Update system is disabled.");
        }

        switch (channel) {
            case STABLE:
            case DEVELOPMENT: {
                if (!IntegrityChecker.isSelfVerified()) {
                    throw new IOException("Self verification failed");
                }

                String url = NetworkUtils.withQuery(Metadata.OFFICIAL_UPDATE_URL, mapOf(
                        pair("version", Metadata.VERSION),
                        pair("channel", channel.channelName)));

                return RemoteVersion.fetch(channel, url);
            }

            case NIGHTLY: {
                if (!Metadata.isNightly()) {
                    throw new IOException("Non nightly version cannot update to a nightly version.");
                }
                if (!GitHubSHAChecker.isSelfVerified()) {
                    throw new IOException("GitHub-SHA doesn't belong to the official repository");
                }

                return RemoteVersion.fetch(channel, Metadata.SNAPSHOT_UPDATE_URL);
            }

            case NONE: {
                throw new IOException("No need to check update with update channel NONE.");
            }

            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    private static boolean isDevelopmentVersion(String version) {
        return version.contains("@") || // eg. @develop@
                version.contains("SNAPSHOT"); // eg. 3.5.SNAPSHOT
    }

    public static void requestCheckUpdate(UpdateChannel channel) {
        Platform.runLater(() -> {
            if (isCheckingUpdate())
                return;
            checkingUpdate.set(true);

            thread(() -> {
                RemoteVersion result = null;
                try {
                    result = checkUpdate(channel);
                    LOG.info("Latest version (" + channel + ") is " + result);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to check for update", e);
                }

                RemoteVersion finalResult = result;
                Platform.runLater(() -> {
                    checkingUpdate.set(false);
                    if (finalResult != null) {
                        latestVersion.set(finalResult);
                    }
                });
            }, "Update Checker", true);
        });
    }
}
