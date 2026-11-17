// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.main;

import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import org.glavo.m3fx.controls.M3ListItem;
import org.glavo.m3fx.controls.M3ListPane;
import org.glavo.m3fx.controls.M3ListSectionHeader;
import org.glavo.m3fx.controls.M3ListStyle;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.ScrollUtils;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Lists community channels and feedback links.
public class FeedbackPage extends SpinnerPane {

    /// Creates the scrollable list of community and feedback links.
    public FeedbackPage() {
        ImageView githubLogo = new ImageView();
        githubLogo.imageProperty().bind(Themes.darkModeProperty().map(dark ->
                FXUtils.newBuiltinImage(dark ? "/assets/img/github-white.png" : "/assets/img/github.png")));

        M3ListPane links = new M3ListPane();
        links.setListStyle(M3ListStyle.SEGMENTED);
        links.getStyleClass().addAll("spinner-pane-content", "feedback-links");
        M3ListItem qq = createLink(i18n("contact.chat.qq_group"), i18n("contact.chat.qq_group.statement"),
                new ImageView(FXUtils.newBuiltinImage("/assets/img/icon.png")), Metadata.GROUPS_URL);
        M3ListItem discord = createLink(i18n("contact.chat.discord"), i18n("contact.chat.discord.statement"),
                new ImageView(FXUtils.newBuiltinImage("/assets/img/discord.png")), "https://discord.gg/jVvC7HfM6U");
        qq.getStyleClass().add("group-start");
        discord.getStyleClass().add("group-end");
        links.getItems().addAll(
                new M3ListSectionHeader(i18n("contact.chat")),
                qq, discord,
                new M3ListSectionHeader(i18n("contact.feedback")),
                createLink(i18n("contact.feedback.github"), i18n("contact.feedback.github.statement"),
                        githubLogo, "https://github.com/HMCL-dev/HMCL/issues/new/choose")
        );

        ScrollPane scrollPane = new ScrollPane(links);
        scrollPane.setFitToWidth(true);
        ScrollUtils.installSmoothScrolling(scrollPane);
        setContent(scrollPane);
    }

    /// Creates a two-line row that opens its link when activated.
    private static M3ListItem createLink(String title, String description, ImageView logo, String url) {
        M3ListItem item = new M3ListItem(title);
        item.setSupportingText(description);
        item.setLeading(logo);
        item.setTrailing(SVG.OPEN_IN_NEW.createIcon());
        item.setOnAction(event -> FXUtils.openLink(url));
        // Keep the skin's hover and ripple layers inside the row's asymmetric corners.
        Region clip = new Region();
        clip.backgroundProperty().bind(item.backgroundProperty());
        item.layoutBoundsProperty().subscribe(bounds -> clip.resize(bounds.getWidth(), bounds.getHeight()));
        item.setClip(clip);
        return item;
    }
}
