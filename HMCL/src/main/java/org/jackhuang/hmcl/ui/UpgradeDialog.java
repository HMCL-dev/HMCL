// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.scene.control.ScrollPane;

import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.LauncherDialog;
import org.jackhuang.hmcl.upgrade.RemoteVersion;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import static org.jackhuang.hmcl.Metadata.CHANGELOG_URL;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class UpgradeDialog extends LauncherDialog {

    public UpgradeDialog(RemoteVersion remoteVersion, Runnable updateRunnable) {
        getDialogPane().setHeadline(i18n("update.changelog"));
        getDialogPane().setContent(FXUtils.newSpinner());

        String url = CHANGELOG_URL + remoteVersion.channel().channelName + ".html";

        Task.supplyAsync(Schedulers.io(), () -> {
            VersionNumber targetVersion = VersionNumber.asVersion(remoteVersion.version());
            VersionNumber currentVersion = VersionNumber.asVersion(Metadata.VERSION);
            if (targetVersion.compareTo(currentVersion) <= 0)
                // Downgrade update, no need to display changelog
                return null;

            Document document = Jsoup.parse(WebURL.toURL(url), 30 * 1000);
            Node node = document.selectFirst("h1[data-version=\"%s\"]".formatted(targetVersion));

            HTMLRenderer renderer = new HTMLRenderer(uri -> {
                LOG.info("Open link: " + uri);
                FXUtils.openLink(uri.toString());
            });

            if (node == null || !"h1".equals(node.nodeName())) {
                LOG.warning("Changelog not found");
                Element list = new Element("ul");
                list.appendElement("li").text(i18n("update.changelog.empty"));
                return renderer.appendNode(list).mergeLineBreaks().render();
            }

            do {
                if ("h1".equals(node.nodeName())) {
                    String changelogVersion = node.attr("data-version");
                    if (StringUtils.isBlank(changelogVersion) || currentVersion.compareTo(changelogVersion) >= 0) {
                        break;
                    }
                }
                renderer.appendNode(node);
                node = node.nextSibling();
            } while (node != null);

            renderer.mergeLineBreaks();
            return renderer.render();
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception == null) {
                if (result != null) {
                    ScrollPane scrollPane = new ScrollPane(result);
                    scrollPane.setFitToWidth(true);
                    ScrollUtils.installSmoothScrolling(scrollPane);
                    getDialogPane().setContent(scrollPane);
                } else {
                    getDialogPane().setContent(null);
                }
            } else {
                LOG.warning("Failed to load update log, trying to open it in browser");
                FXUtils.openLink(url);
                getDialogPane().setContent(null);
            }
        }).start();

        var openInBrowser = new M3Button(i18n("web.view_in_browser"), M3ButtonVariant.TEXT);
        openInBrowser.setOnAction(event -> {
            event.consume();
            FXUtils.openLink(url);
        });

        M3Button updateButton = new M3Button(i18n("update.accept"), M3ButtonVariant.TEXT);
        updateButton.setOnAction(event -> {
            event.consume();
            updateRunnable.run();
        });

        M3Button cancelButton = new M3Button(i18n("button.cancel"), M3ButtonVariant.TEXT);
        getDialogPane().getActions().setAll(openInBrowser, cancelButton, updateButton);
        cancelButton.setCancelButton(true);
    }
}
