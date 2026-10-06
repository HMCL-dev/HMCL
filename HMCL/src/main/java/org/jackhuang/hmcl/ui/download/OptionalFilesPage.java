/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.download;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.modpack.ModpackFile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.JFXHyperlink;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Wizard page that lets the user choose which optional modpack files to install.
@NotNullByDefault
public class OptionalFilesPage extends SpinnerPane implements WizardPage {

    /// Fixed row height used to size [#body] to its items.
    private static final double CELL_HEIGHT = 48;

    /// Maximum list viewport height before scrolling.
    private static final double MAX_LIST_HEIGHT = 320;

    /// Keys of files the user unchecked; empty means all optional files will be installed.
    private final ObservableSet<String> excludedFiles;

    /// Per-file selected state mirrored from [#excludedFiles] (`true` means install / not excluded).
    private final Map<String, BooleanProperty> selectedByKey = new HashMap<>();

    /// Whether [#selectedByKey] is being updated from an [#excludedFiles] set change.
    private boolean updatingFromSet;

    /// List view showing optional files.
    private final JFXListView<ModpackFile> body = new JFXListView<>();

    /// Creates the optional-files selection page.
    ///
    /// @param install         callback that finishes the install wizard
    /// @param retry           callback that reloads optional-file metadata
    /// @param loading         whether remote metadata is still loading
    /// @param successful      whether remote metadata loaded successfully
    /// @param optionalFiles   optional files to display
    /// @param excludedFiles   mutable set of unchecked optional file keys
    public OptionalFilesPage(
            Runnable install,
            Runnable retry,
            ObservableBooleanValue loading,
            ObservableBooleanValue successful,
            ObservableList<ModpackFile> optionalFiles,
            ObservableSet<String> excludedFiles) {
        this.excludedFiles = excludedFiles;
        excludedFiles.addListener((SetChangeListener<String>) change -> {
            updatingFromSet = true;
            try {
                if (change.wasRemoved()) {
                    BooleanProperty selected = selectedByKey.get(change.getElementRemoved());
                    if (selected != null)
                        selected.set(true);
                }
                if (change.wasAdded()) {
                    BooleanProperty selected = selectedByKey.get(change.getElementAdded());
                    if (selected != null)
                        selected.set(false);
                }
            } finally {
                updatingFromSet = false;
            }
        });

        VBox borderPane = new VBox();
        borderPane.setAlignment(Pos.CENTER);
        borderPane.setMaxHeight(Region.USE_PREF_SIZE);
        FXUtils.setLimitWidth(borderPane, 500);

        ComponentList componentList = new ComponentList();

        Label lblRetry = new Label(i18n("modpack.retry_optional_files"));
        lblRetry.setOnMouseClicked(e -> retry.run());

        VBox tail = new VBox();
        {
            var descPane = new BorderPane();
            HBox selectionButtons = new HBox(8);
            JFXButton btnSelectAll = FXUtils.newBorderButton(i18n("button.select_all"));
            btnSelectAll.setOnAction(e -> excludedFiles.clear());
            JFXButton btnClear = FXUtils.newBorderButton(i18n("button.clear"));
            btnClear.setOnAction(e -> {
                excludedFiles.clear();
                for (ModpackFile file : optionalFiles) {
                    excludedFiles.add(file.key());
                }
            });
            selectionButtons.getChildren().setAll(btnSelectAll, btnClear);
            descPane.setLeft(selectionButtons);

            var btnInstall = FXUtils.newRaisedButton(i18n("button.install"));
            descPane.setRight(btnInstall);
            btnInstall.setOnAction(e -> install.run());
            tail.getChildren().add(descPane);
        }

        // ListView defaults to a large preferred height (~400) regardless of item count, which
        // leaves empty viewport space when few optional files exist. Size exactly to content —
        // do not add extra pixels, or a blank strip appears under the last row.
        body.setFixedCellSize(CELL_HEIGHT);
        body.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.min(MAX_LIST_HEIGHT, optionalFiles.size() * CELL_HEIGHT + 2),
                optionalFiles));
        body.setMaxHeight(Region.USE_PREF_SIZE);
        body.setCellFactory(it -> new OptionalFileEntry(body));
        body.setItems(optionalFiles);

        Runnable refreshContent = () -> {
            if (successful.get()) {
                componentList.getContent().setAll(body, tail);
            } else {
                componentList.getContent().setAll(lblRetry, body, tail);
            }
        };
        refreshContent.run();
        successful.addListener((obs, oldVal, newVal) -> refreshContent.run());

        borderPane.getChildren().setAll(componentList);
        setContent(borderPane);
        loadingProperty().bind(loading);
    }

    /// Returns the selected property for `key`, creating and wiring it on first use.
    ///
    /// @param key the file exclusion key
    /// @return a property that is `true` when the file is selected for install
    private BooleanProperty selectedProperty(String key) {
        return selectedByKey.computeIfAbsent(key, k -> {
            BooleanProperty selected = new SimpleBooleanProperty(!excludedFiles.contains(k));
            selected.addListener((obs, wasSelected, isSelected) -> {
                if (updatingFromSet)
                    return;
                if (isSelected) {
                    excludedFiles.remove(k);
                } else {
                    excludedFiles.add(k);
                }
            });
            return selected;
        });
    }

    /// List cell for one optional file with checkbox and detail button.
    private final class OptionalFileEntry extends MDListCell<ModpackFile> {
        private final JFXCheckBox checkBox = new JFXCheckBox();
        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton infoButton = new JFXButton();
        private final HBox container = new HBox(8);

        /// Currently bidirectionally bound selected property, or `null` when unbound.
        private @Nullable BooleanProperty boundSelected = null;

        /// Creates a cell bound to `listView`.
        ///
        /// @param listView the owning list view
        public OptionalFileEntry(JFXListView<ModpackFile> listView) {
            super(listView);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);
            setSelectable();
            container.getChildren().setAll(checkBox, content);

            infoButton.getStyleClass().add("toggle-icon4");
            infoButton.setGraphic(SVG.INFO.createIcon());
            container.getChildren().add(infoButton);
            getContainer().getChildren().setAll(container);
        }

        @Override
        protected void updateControl(ModpackFile item, boolean empty) {
            if (empty) {
                if (boundSelected != null) {
                    checkBox.selectedProperty().unbindBidirectional(boundSelected);
                    boundSelected = null;
                }
                return;
            }
            String name = item.fileName();
            if (name != null) {
                content.setTitle(name);
            } else {
                content.setTitle(i18n("modpack.unknown_optional_file"));
            }
            RemoteAddon addon = item.remoteAddon();
            if (addon != null) {
                content.setSubtitle(addon.title());
                infoButton.setOnMouseClicked(e -> Controllers.dialog(new ModInfo(addon)));
                infoButton.setManaged(true);
                infoButton.setVisible(true);
            } else {
                content.setSubtitle("");
                infoButton.setOnMouseClicked(null);
                infoButton.setManaged(false);
                infoButton.setVisible(false);
            }
            BooleanProperty selected = OptionalFilesPage.this.selectedProperty(item.key());
            if (boundSelected != selected) {
                if (boundSelected != null)
                    checkBox.selectedProperty().unbindBidirectional(boundSelected);
                checkBox.selectedProperty().bindBidirectional(selected);
                boundSelected = selected;
            }
        }
    }

    /// Dialog showing remote addon details for an optional file.
    private static final class ModInfo extends JFXDialogLayout {
        /// Creates a detail dialog for `addon`.
        ///
        /// @param addon the remote addon
        public ModInfo(RemoteAddon addon) {
            HBox container = new HBox(8);
            SpinnerPane spinnerPane = new SpinnerPane();
            ImageView imageView = new ImageView();
            imageView.setFitHeight(32);
            imageView.setFitWidth(32);
            spinnerPane.setContent(imageView);
            spinnerPane.setPrefSize(32, 32);
            spinnerPane.setLoading(true);
            CompletableFuture.supplyAsync(() -> {
                try {
                    return FXUtils.getRemoteImageTask(addon.iconUrl(), 32, 32, true, true).run();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, Schedulers.io()).thenAcceptAsync((image) -> {
                imageView.setImage(image);
                spinnerPane.setLoading(false);
            }, Schedulers.javafx());
            container.getChildren().add(spinnerPane);

            TwoLineListItem title = new TwoLineListItem();
            title.setTitle(addon.title());
            title.setSubtitle(addon.author());
            container.getChildren().add(title);
            setHeading(container);

            Label description = new Label(addon.description());
            description.setWrapText(true);
            description.setPadding(new Insets(8, 0, 0, 0));
            setBody(description);

            JFXHyperlink pageButton = new JFXHyperlink(i18n("mods.url"));
            pageButton.setOnAction(e -> FXUtils.openLink(addon.pageUrl()));
            getActions().add(pageButton);

            JFXButton okButton = new JFXButton();
            okButton.getStyleClass().add("dialog-accept");
            okButton.setText(i18n("button.ok"));
            okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            getActions().add(okButton);

            onEscPressed(this, okButton::fire);
        }
    }

    @Override
    public String getTitle() {
        return i18n("modpack.optional_files");
    }

    @Override
    public void cleanup(SettingsMap settings) {
    }
}
