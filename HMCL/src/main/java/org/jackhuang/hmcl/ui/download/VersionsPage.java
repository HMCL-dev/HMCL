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
package org.jackhuang.hmcl.ui.download;

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXSpinner;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.fxml.FXML;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionHandler;
import org.jackhuang.hmcl.ui.wizard.Refreshable;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class VersionsPage extends StackPane implements WizardPage, Refreshable {
    private final String gameVersion;
    private final DownloadProvider downloadProvider;
    private final String libraryId;
    private final String title;

    @FXML
    private JFXListView<VersionsPageItem> list;
    @FXML
    private JFXSpinner spinner;
    @FXML
    private StackPane failedPane;
    @FXML
    private JFXCheckBox chkRelease;
    @FXML
    private JFXCheckBox chkSnapshot;
    @FXML
    private JFXCheckBox chkOld;
    @FXML
    private HBox checkPane;
    @FXML
    private VBox centrePane;

    private final TransitionHandler transitionHandler = new TransitionHandler(this);
    private final VersionList<?> versionList;
    private TaskExecutor executor;

    public VersionsPage(WizardController controller, String title, String gameVersion, DownloadProvider downloadProvider, String libraryId, Runnable callback) {
        this.title = title;
        this.gameVersion = gameVersion;
        this.downloadProvider = downloadProvider;
        this.libraryId = libraryId;
        this.versionList = downloadProvider.getVersionListById(libraryId);

        FXUtils.loadFXML(this, "/assets/fxml/download/versions.fxml");

        if (versionList.hasType()) {
            centrePane.getChildren().setAll(checkPane, list);
        } else
            centrePane.getChildren().setAll(list);

        InvalidationListener listener = o -> list.getItems().setAll(loadVersions());
        chkRelease.selectedProperty().addListener(listener);
        chkSnapshot.selectedProperty().addListener(listener);
        chkOld.selectedProperty().addListener(listener);

        list.setOnMouseClicked(e -> {
            if (list.getSelectionModel().getSelectedIndex() < 0)
                return;
            controller.getSettings().put(libraryId, list.getSelectionModel().getSelectedItem().getRemoteVersion());
            callback.run();
        });
        refresh();
    }

    private List<VersionsPageItem> loadVersions() {
        return versionList.getVersions(gameVersion).stream()
                .filter(it -> {
                    switch (it.getVersionType()) {
                        case RELEASE:
                            return chkRelease.isSelected();
                        case SNAPSHOT:
                            return chkSnapshot.isSelected();
                        case OLD:
                            return chkOld.isSelected();
                        default:
                            return true;
                    }
                })
                .sorted()
                .map(VersionsPageItem::new).collect(Collectors.toList());
    }

    @Override
    public void refresh() {
        getChildren().setAll(spinner);
        executor = versionList.refreshAsync(downloadProvider).finalized((variables, isDependentsSucceeded) -> {
            if (isDependentsSucceeded) {
                List<VersionsPageItem> items = loadVersions();

                Platform.runLater(() -> {
                    list.getItems().setAll(items);
                    transitionHandler.setContent(centrePane, ContainerAnimations.FADE.getAnimationProducer());
                });
            } else {
                Platform.runLater(() -> {
                    transitionHandler.setContent(failedPane, ContainerAnimations.FADE.getAnimationProducer());
                });
            }
        }).executor().start();
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
        settings.remove(libraryId);
        if (executor != null)
            executor.cancel();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }
}
