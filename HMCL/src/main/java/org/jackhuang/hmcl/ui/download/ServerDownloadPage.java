// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.download;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.ComponentRemoteVersion;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Lists official Minecraft versions and downloads their server jar.
public final class ServerDownloadPage extends VBox {
    private static final String OFFICIAL_HOST = "piston-data.mojang.com";
    private static final String MIRROR_HOST = "bmclapi2.bangbang93.com";

    private final DownloadProvider downloadProvider = DownloadProviders.getDownloadProvider();
    private final ListView<ComponentRemoteVersion> versionList = new ListView<>();

    public ServerDownloadPage() {
        setSpacing(8);
        setPadding(new Insets(16));

        versionList.setPlaceholder(new Label(i18n("download.server.loading")));
        versionList.setCellFactory(list -> new ServerVersionCell());
        VBox.setVgrow(versionList, Priority.ALWAYS);

        Label description = new Label(i18n("download.server.description"));
        description.setWrapText(true);

        getChildren().addAll(description, versionList);
        loadVersions();
    }

    private void loadVersions() {
        downloadProvider.getVersionsAsync(GameComponentType.GAME, null, false)
                .whenComplete(Schedulers.javafx(), (versions, exception) -> {
                    if (exception != null) {
                        LOG.warning("Failed to fetch server versions", exception);
                        versionList.setPlaceholder(new Label(i18n("download.server.failed")));
                    } else {
                        versionList.getItems().setAll(versions);
                    }
                })
                .start();
    }

    private void download(ComponentRemoteVersion version) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(i18n("download.server.choose"));
        Path directory = Controllers.showDialog(chooser);
        if (directory == null) {
            return;
        }

        Path target = directory.resolve("server-" + version.getSelfVersion() + ".jar");

        Task<String> metadataTask = new GetTask(WebURL.parse(version.getUrls().get(0)));
        Task<?> task = metadataTask.thenComposeAsync(json -> {
            VersionMetadata metadata = JsonUtils.fromNonNullJson(json, VersionMetadata.class);
            ServerDownload download = metadata.downloads() != null ? metadata.downloads().server() : null;
            if (download == null) {
                throw new IOException("No server download available for " + version.getSelfVersion());
            }

            List<WebURL> candidates = new ArrayList<>(2);
            candidates.add(WebURL.parse(download.url()));
            if (download.url().contains(OFFICIAL_HOST)) {
                candidates.add(WebURL.parse(download.url().replace(OFFICIAL_HOST, MIRROR_HOST)));
            }

            return new FileDownloadTask(candidates, target,
                    FileDownloadTask.IntegrityCheck.of("SHA-1", download.sha1()));
        });

        Controllers.taskDialog(task,
                i18n("message.downloading.file", target.getFileName().toString()),
                TaskCancellationAction.NORMAL);
    }

    private final class ServerVersionCell extends ListCell<ComponentRemoteVersion> {
        private final Label name = new Label();
        private final Button action = new Button(i18n("download"));
        private final HBox box = new HBox(8, name, new Region(), action);

        private ServerVersionCell() {
            HBox.setHgrow(box.getChildren().get(1), Priority.ALWAYS);
            action.setOnAction(event -> {
                ComponentRemoteVersion item = getItem();
                if (item != null) {
                    download(item);
                }
            });
        }

        @Override
        protected void updateItem(ComponentRemoteVersion item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                name.setText(item.getSelfVersion());
                setGraphic(box);
            }
        }
    }

    private record VersionMetadata(Downloads downloads) {
    }

    private record Downloads(ServerDownload server) {
    }

    private record ServerDownload(String url, String sha1) {
    }
}
