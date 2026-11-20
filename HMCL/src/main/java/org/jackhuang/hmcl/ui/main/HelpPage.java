// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.main;

import com.google.gson.annotations.SerializedName;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.glavo.m3fx.controls.M3ListPane;
import org.glavo.m3fx.controls.M3ListSectionHeader;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ScrollUtils;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.io.HttpRequest;

import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class HelpPage extends SpinnerPane {

    private final VBox content;

    public HelpPage() {
        content = new VBox();
        content.getStyleClass().add("spinner-pane-content");
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        ScrollUtils.installSmoothScrolling(scrollPane);
        setContent(scrollPane);

        var docPane = FXUtils.newExternalLinkItem(Metadata.DOCS_URL);

        docPane.setHeadlineText(i18n("help.doc"));
        docPane.setSupportingText(i18n("help.detail"));

        M3ListPane doc = FXUtils.newSegmentedList();
        doc.getItems().setAll(docPane);
        content.getChildren().add(doc);

        loadHelp();
    }

    private void loadHelp() {
        showSpinner();
        Task.supplyAsync(() -> HttpRequest.GET(Metadata.DOCS_URL + "/index.json").getJson(listTypeOf(HelpCategory.class)))
            .whenComplete(Schedulers.javafx(), (helpCategories, exception) -> {
                if (exception != null) {
                    LOG.warning("Failed to load help page: ", exception);
                    showLoadFailure();
                    return;
                }

                try {
                    List<Node> nodes = new ArrayList<>();

                    for (HelpCategory category : helpCategories) {
                        M3ListPane categoryPane = FXUtils.newSegmentedList();

                        for (Help help : category.items()) {
                            var item = FXUtils.newExternalLinkItem(help.url());

                            item.setHeadlineText(help.title());
                            item.setSupportingText(help.subtitle());

                            categoryPane.getItems().add(item);
                        }

                        nodes.add(new M3ListSectionHeader(category.title()));
                        nodes.add(categoryPane);
                    }

                    content.getChildren().addAll(nodes);

                    hideSpinner();
                } catch (Exception e) {
                    LOG.warning("Failed to display help page: ", e);
                    showLoadFailure();
                }
            }).start();
    }

    private void showLoadFailure() {
        setFailedReason(i18n("download.failed.refresh"));
        setOnFailedAction(e -> loadHelp());
        setLoading(false);
    }

    @JsonSerializable
    private record HelpCategory(
            @SerializedName("title") String title,
            @SerializedName("items") List<Help> items) {
    }

    @JsonSerializable
    private record Help(
            @SerializedName("title") String title,
            @SerializedName("subtitle") String subtitle,
            @SerializedName("url") String url) {
    }
}
