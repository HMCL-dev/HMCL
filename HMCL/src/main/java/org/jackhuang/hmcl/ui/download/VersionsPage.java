/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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

import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXSpinner;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionHandler;
import org.jackhuang.hmcl.ui.wizard.Refreshable;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;

import java.util.Map;

public final class VersionsPage extends StackPane implements WizardPage, Refreshable {
    private final WizardController controller;
    private final String gameVersion;
    private final DownloadProvider downloadProvider;
    private final String libraryId;
    private final Runnable callback;
    private final String title;

    @FXML
    private JFXListView<VersionsPageItem> list;
    @FXML private JFXSpinner spinner;

    private final TransitionHandler transitionHandler = new TransitionHandler(this);
    private final VersionList<?> versionList;
    private TaskExecutor executor;

    public VersionsPage(WizardController controller, String title, String gameVersion, DownloadProvider downloadProvider, String libraryId, Runnable callback) {
        this.controller = controller;
        this.title = title;
        this.gameVersion = gameVersion;
        this.downloadProvider = downloadProvider;
        this.libraryId = libraryId;
        this.callback = callback;

        this.versionList = downloadProvider.getVersionListById(libraryId);

        FXUtils.loadFXML(this, "/assets/fxml/download/versions.fxml");
        getChildren().setAll(spinner);
        list.getSelectionModel().selectedItemProperty().addListener((a, b, newValue) -> {
            controller.getSettings().put(libraryId, newValue.getRemoteVersion().getSelfVersion());
            callback.run();
        });
        refresh();
    }

    @Override
    public void refresh() {
        executor = versionList.refreshAsync(downloadProvider).subscribe(Schedulers.javafx(), () -> {
            versionList.getVersions(gameVersion).stream()
                    .sorted(RemoteVersion.RemoteVersionComparator.INSTANCE)
                    .forEach(version -> {
                        list.getItems().add(new VersionsPageItem(version));
                    });

            transitionHandler.setContent(list, ContainerAnimations.FADE.getAnimationProducer());
        });
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
}
