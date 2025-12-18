/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.JFXHyperlink;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

final class DatapackInfoDialog extends JFXDialogLayout {
    public DatapackInfoDialog(DatapackListPageSkin.DatapackInfoObject datapackInfoObject, Profile profile, String versionID) {

        Stage stage = Controllers.getStage();
        {
            maxWidthProperty().bind(stage.widthProperty().multiply(0.7));
        }

        //heading area
        HBox titleContainer = new HBox();
        {
            titleContainer.setSpacing(8);
            setHeading(titleContainer);
        }
        {
            TwoLineListItem title = new TwoLineListItem();
            {
                title.setTitle(datapackInfoObject.getTitle());
            }

            ImageView imageView = new ImageView();
            {
                FXUtils.limitSize(imageView, 40, 40);
                datapackInfoObject.loadIcon(imageView, null);
            }

            titleContainer.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), title);
        }

        //body area
        Label description = new Label(datapackInfoObject.getSubtitle());
        {
            description.setWrapText(true);
            FXUtils.copyOnDoubleClick(description);
        }
        ScrollPane descriptionPane = new ScrollPane(description);
        {
            FXUtils.smoothScrolling(descriptionPane);
            descriptionPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            descriptionPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            descriptionPane.setFitToWidth(true);
            description.heightProperty().addListener((obs, oldVal, newVal) -> {
                double maxHeight = stage.getHeight() * 0.5;
                double targetHeight = Math.min(newVal.doubleValue(), maxHeight);
                descriptionPane.setPrefViewportHeight(targetHeight);
            });

            setBody(descriptionPane);
        }

        //action area
        JFXHyperlink openInMcModButton = new JFXHyperlink(i18n("mods.mcmod.search"));
        {
            openInMcModButton.setOnAction(e -> {
                fireEvent(new DialogCloseEvent());
                FXUtils.openLink(NetworkUtils.withQuery("https://search.mcmod.cn/s", mapOf(
                        pair("key", datapackInfoObject.getTitle()),
                        pair("site", "all"),
                        pair("filter", "0")
                )));
            });
        }

        for (Pair<String, ? extends RemoteModRepository> item : Arrays.asList(
                pair("mods.curseforge", CurseForgeRemoteModRepository.MODS),
                pair("mods.modrinth", ModrinthRemoteModRepository.MODS)
        )) {
            RemoteModRepository repository = item.getValue();
            JFXHyperlink button = new JFXHyperlink(i18n(item.getKey()));
            Task.runAsync(() -> {
                Optional<RemoteMod.Version> versionOptional = repository.getRemoteVersionByLocalFile(null, datapackInfoObject.getPackInfo().getPath());
                versionOptional.ifPresent(version -> {
                    RemoteMod remoteMod;
                    try {
                        remoteMod = repository.getModById(version.getModid());
                    } catch (IOException e) {
                        LOG.warning("Cannot get remote mod of " + version.getModid(), e);
                        return;
                    }

                    FXUtils.runInFX(() -> {
                        button.setOnAction(e -> {
                            fireEvent(new DialogCloseEvent());
                            Controllers.navigate(new DownloadPage(
                                    repository instanceof CurseForgeRemoteModRepository ? HMCLLocalizedDownloadListPage.ofCurseForgeMod(null, false) : HMCLLocalizedDownloadListPage.ofModrinthMod(null, false),
                                    remoteMod,
                                    new Profile.ProfileVersion(profile, versionID),
                                    null
                            ));
                        });
                        button.setDisable(false);

                        ModTranslations.Mod modToOpenInMcMod = ModTranslations.getTranslationsByRepositoryType(repository.getType()).getModByCurseForgeId(remoteMod.getSlug());
                        if (modToOpenInMcMod != null) {
                            openInMcModButton.setOnAction(e -> {
                                fireEvent(new DialogCloseEvent());
                                FXUtils.openLink(ModTranslations.MOD.getMcmodUrl(modToOpenInMcMod));
                            });
                            openInMcModButton.setText(i18n("mods.mcmod.page"));
                        }
                    });
                });
            }).start();
            button.setDisable(true);
            getActions().add(button);
        }

        JFXButton okButton = new JFXButton();
        {
            okButton.getStyleClass().add("dialog-accept");
            okButton.setText(i18n("button.ok"));
            okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
        }

        getActions().addAll(openInMcModButton, okButton);

        onEscPressed(this, okButton::fire);
    }
}