/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXScrollPane;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.IconedTwoLineListItem;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class AboutPage extends StackPane {

    public AboutPage() {
        ComponentList about = new ComponentList();
        {
            IconedTwoLineListItem launcher = new IconedTwoLineListItem();
            launcher.setImage(new Image("/assets/img/craft_table.png", 32, 32, false, true));
            launcher.setTitle("Hello Minecraft! Launcher");
            launcher.setSubtitle(Metadata.VERSION);
            launcher.setExternalLink("https://hmcl.huangyuhui.net");

            IconedTwoLineListItem author = new IconedTwoLineListItem();
            author.setImage(new Image("/assets/img/yellow_fish.jpg", 32, 32, false, true));
            author.setTitle("huanghongxun");
            author.setSubtitle("https://www.huangyuhui.net");
            author.setExternalLink("https://www.huangyuhui.net");

            about.getContent().setAll(launcher, author);
        }

        ComponentList thanks = new ComponentList();
        {
            IconedTwoLineListItem yushijinhun = new IconedTwoLineListItem();
            yushijinhun.setImage(new Image("/assets/img/yushijinhun.jpg", 32, 32, false, true));
            yushijinhun.setTitle("yushijinhun");
            yushijinhun.setSubtitle(i18n("about.thanks_to.yushijinhun.statement"));
            yushijinhun.setExternalLink("https://yushi.moe/");

            IconedTwoLineListItem bangbang93 = new IconedTwoLineListItem();
            bangbang93.setImage(new Image("/assets/img/bangbang93.jpg", 32, 32, false, true));
            bangbang93.setTitle("bangbang93");
            bangbang93.setSubtitle(i18n("about.thanks_to.bangbang93.statement"));
            bangbang93.setExternalLink("https://bmclapi2.bangbang93.com/");

            IconedTwoLineListItem gamerteam = new IconedTwoLineListItem();
            gamerteam.setTitle("gamerteam");
            gamerteam.setImage(new Image("/assets/img/gamerteam.jpg", 32, 32, false, true));
            gamerteam.setSubtitle(i18n("about.thanks_to.gamerteam.statement"));

            IconedTwoLineListItem redLnn = new IconedTwoLineListItem();
            redLnn.setTitle("Red_lnn");
            redLnn.setImage(new Image("/assets/img/red_lnn.jpg", 32, 32, false, true));
            redLnn.setSubtitle(i18n("about.thanks_to.red_lnn.statement"));

            IconedTwoLineListItem mcbbs = new IconedTwoLineListItem();
            mcbbs.setImage(new Image("/assets/img/chest.png", 32, 32, false, true));
            mcbbs.setTitle(i18n("about.thanks_to.mcbbs"));
            mcbbs.setSubtitle(i18n("about.thanks_to.mcbbs.statement"));
            mcbbs.setExternalLink("https://www.mcbbs.net/");

            IconedTwoLineListItem mcmod = new IconedTwoLineListItem();
            mcmod.setImage(new Image("/assets/img/mcmod.png", 32, 32, false, true));
            mcmod.setTitle(i18n("about.thanks_to.mcmod"));
            mcmod.setSubtitle(i18n("about.thanks_to.mcmod.statement"));
            mcmod.setExternalLink("https://www.mcmod.cn/");

            IconedTwoLineListItem noin = new IconedTwoLineListItem();
            noin.setImage(new Image("/assets/img/noin.png", 32, 32, false, true));
            noin.setTitle(i18n("about.thanks_to.noin"));
            noin.setSubtitle(i18n("about.thanks_to.noin.statement"));
            noin.setExternalLink("https://noin.cn/");

            IconedTwoLineListItem contributors = new IconedTwoLineListItem();
            contributors.setImage(new Image("/assets/img/github.png", 32, 32, false, true));
            contributors.setTitle(i18n("about.thanks_to.contributors"));
            contributors.setSubtitle(i18n("about.thanks_to.contributors.statement"));
            contributors.setExternalLink("https://github.com/huanghongxun/HMCL/graphs/contributors");

            IconedTwoLineListItem users = new IconedTwoLineListItem();
            users.setImage(new Image("/assets/img/craft_table.png", 32, 32, false, true));
            users.setTitle(i18n("about.thanks_to.users"));
            users.setSubtitle(i18n("about.thanks_to.users.statement"));
            users.setExternalLink("https://hmcl.huangyuhui.net/api/redirect/sponsor");

            thanks.getContent().setAll(yushijinhun, bangbang93, mcbbs, mcmod, noin, gamerteam, redLnn, users, contributors);
        }

        ComponentList dep = new ComponentList();
        {
            IconedTwoLineListItem javafx = new IconedTwoLineListItem();
            javafx.setTitle("JavaFX");
            javafx.setSubtitle("Copyright (c) 2013, 2021, Oracle and/or its affiliates.\nLicensed under the GPL 2 with Classpath Exception.");
            javafx.setExternalLink("https://openjfx.io/");

            IconedTwoLineListItem jfoenix = new IconedTwoLineListItem();
            jfoenix.setTitle("JFoenix");
            jfoenix.setSubtitle("Copyright (c) 2016 JFoenix.\nLicensed under the MIT License.");
            jfoenix.setExternalLink("http://www.jfoenix.com/");

            IconedTwoLineListItem gson = new IconedTwoLineListItem();
            gson.setTitle("Gson");
            gson.setSubtitle("Copyright 2008 Google Inc.\nLicensed under the Apache 2.0 License.");
            gson.setExternalLink("https://github.com/google/gson");

            IconedTwoLineListItem xz = new IconedTwoLineListItem();
            xz.setTitle("XZ for Java");
            xz.setSubtitle("Lasse Collin, Igor Pavlov, and/or Brett Okken.\nPublic Domain.");
            xz.setExternalLink("https://tukaani.org/xz/java.html");

            IconedTwoLineListItem fxgson = new IconedTwoLineListItem();
            fxgson.setTitle("fx-gson");
            fxgson.setSubtitle("Copyright (c) 2016 Joffrey Bion.\nLicensed under the MIT License.");
            fxgson.setExternalLink("https://github.com/joffrey-bion/fx-gson");

            IconedTwoLineListItem constantPoolScanner = new IconedTwoLineListItem();
            constantPoolScanner.setTitle("Constant Pool Scanner");
            constantPoolScanner.setSubtitle("Copyright 1997-2010 Oracle and/or its affiliates.\nLicensed under the GPL 2 or the CDDL.");
            constantPoolScanner.setExternalLink("https://github.com/jenkinsci/constant-pool-scanner");

            IconedTwoLineListItem openNBT = new IconedTwoLineListItem();
            openNBT.setTitle("OpenNBT");
            openNBT.setSubtitle("Copyright (C) 2013-2021 Steveice10.\nLicensed under the MIT License.");
            openNBT.setExternalLink("https://github.com/Steveice10/OpenNBT");

            dep.getContent().setAll(javafx, jfoenix, gson, xz, fxgson, constantPoolScanner, openNBT);
        }

        ComponentList legal = new ComponentList();
        {
            TwoLineListItem copyright = new TwoLineListItem();
            copyright.setTitle(i18n("about.copyright"));
            copyright.setSubtitle(i18n("about.copyright.statement"));

            TwoLineListItem claim = new TwoLineListItem();
            claim.setTitle(i18n("about.claim"));
            claim.setSubtitle(i18n("about.claim.statement"));

            TwoLineListItem openSource = new TwoLineListItem();
            openSource.setTitle(i18n("about.open_source"));
            openSource.setSubtitle(i18n("about.open_source.statement"));

            legal.getContent().setAll(copyright, claim, openSource);
        }

        VBox content = new VBox(16);
        content.setPadding(new Insets(10));
        content.getChildren().setAll(
                ComponentList.createComponentListTitle(i18n("about")),
                about,

                ComponentList.createComponentListTitle(i18n("about.thanks_to")),
                thanks,

                ComponentList.createComponentListTitle(i18n("about.dependency")),
                dep,

                ComponentList.createComponentListTitle(i18n("about.legal")),
                legal
        );


        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        JFXScrollPane.smoothScrolling(scrollPane);
        getChildren().setAll(scrollPane);
    }
}
