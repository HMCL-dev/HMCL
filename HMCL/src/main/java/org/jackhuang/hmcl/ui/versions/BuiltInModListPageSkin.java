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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SkinBase;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class BuiltInModListPageSkin extends SkinBase<BuiltInModListPage> {

    private final TransitionPane toolbarPane;
    private final HBox searchBar;
    private final HBox toolbarNormal;

    private final JFXListView<ModListPageSkin.ModInfoObject> listView;
    private final JFXTextField searchField;

    private boolean isSearching = false;

    protected BuiltInModListPageSkin(BuiltInModListPage skinnable) {
        super(skinnable);

        StackPane pane = new StackPane();
        pane.setPadding(new Insets(10));
        pane.getStyleClass().addAll("notice-pane");

        ComponentList root = new ComponentList();
        root.getStyleClass().add("no-padding");
        listView = new JFXListView<>();

        {
            toolbarPane = new TransitionPane();

            searchBar = new HBox();
            toolbarNormal = new HBox();

            searchBar.setAlignment(Pos.CENTER);
            searchBar.setPadding(new Insets(0, 5, 0, 5));
            searchField = new JFXTextField();
            searchField.setPromptText(i18n("search"));
            HBox.setHgrow(searchField, Priority.ALWAYS);
            PauseTransition pause = new PauseTransition(Duration.millis(100));
            pause.setOnFinished(e -> search());
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                pause.setRate(1);
                pause.playFromStart();
            });

            JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE,
                    () -> {
                        changeToolbar(toolbarNormal);
                        isSearching = false;
                        searchField.clear();
                        Bindings.bindContent(listView.getItems(), getSkinnable().getItems());
                    });

            onEscPressed(searchField, closeSearchBar::fire);

            searchBar.getChildren().setAll(searchField, closeSearchBar);

            toolbarNormal.getChildren().addAll(
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                    createToolbarButton2(i18n("mods.built_in.export.jij_info.all"), SVG.FILE_EXPORT, () -> exportAllJijList(listView.getItems())),
                    createToolbarButton2(i18n("search"), SVG.SEARCH, () -> changeToolbar(searchBar))
            );

            root.getContent().add(toolbarPane);
            changeToolbar(toolbarNormal);
        }

        {
            SpinnerPane center = new SpinnerPane();
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.getStyleClass().add("large-spinner-pane");
            center.loadingProperty().bind(skinnable.loadingProperty());

            listView.setCellFactory(param -> new JijModListCell(listView));
            Bindings.bindContent(listView.getItems(), skinnable.getItems());

            center.setContent(listView);
            root.getContent().add(center);
        }

        pane.getChildren().add(root);
        getChildren().add(pane);
    }

    private void changeToolbar(HBox newToolbar) {
        Node oldToolbar = toolbarPane.getCurrentNode();
        if (newToolbar != oldToolbar) {
            toolbarPane.setContent(newToolbar, ContainerAnimations.FADE);
            if (newToolbar == searchBar) {
                Platform.runLater(searchField::requestFocus);
            }
        }
    }

    private void search() {
        isSearching = true;
        Bindings.unbindContent(listView.getItems(), getSkinnable().getItems());

        String queryString = searchField.getText();
        if (StringUtils.isBlank(queryString)) {
            listView.getItems().setAll(getSkinnable().getItems());
        } else {
            listView.getItems().clear();
            String lowerQueryString = queryString.toLowerCase(Locale.ROOT);
            Predicate<String> predicate = s -> s != null && s.toLowerCase(Locale.ROOT).contains(lowerQueryString);

            for (ModListPageSkin.ModInfoObject item : getSkinnable().getItems()) {
                LocalModFile modInfo = item.getModInfo();
                if (predicate.test(modInfo.getFileName())
                        || predicate.test(modInfo.getName())
                        || predicate.test(modInfo.getId())
                        || (item.getModTranslations() != null && predicate.test(item.getModTranslations().getDisplayName()))) {
                    listView.getItems().add(item);
                }
            }
        }
    }

    private class JijModListCell extends MDListCell<ModListPageSkin.ModInfoObject> {

        private final ImageView imageView = new ImageView();
        private final TwoLineListItem content = new TwoLineListItem();
        private JFXPopup activePopup;
        private boolean ignoreNextClick = false;

        public JijModListCell(JFXListView<ModListPageSkin.ModInfoObject> listView) {
            super(listView);
            this.getStyleClass().add("mod-info-list-cell");

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            StackPane.setMargin(container, new Insets(8, 8, 8, 18));

            imageView.setFitWidth(24);
            imageView.setFitHeight(24);
            imageView.setPreserveRatio(true);

            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);

            container.getChildren().addAll(imageView, content);
            getContainer().getChildren().setAll(container);

            setSelectable();

            this.setOnMousePressed(e -> {
                if (activePopup != null && activePopup.isShowing()) {
                    ignoreNextClick = true;
                } else {
                    ignoreNextClick = false;
                }
            });

            this.setOnMouseClicked(e -> {
                if (getItem() != null && getItem().getModInfo() != null) {
                    LocalModFile modFile = getItem().getModInfo();
                    if (modFile.hasBundledMods()) {

                        if (activePopup != null && activePopup.isShowing()) {
                            activePopup.hide();
                            activePopup = null;
                            return;
                        }

                        activePopup = showBundledPopup(this, modFile.getName(), modFile.getBundledMods());

                        activePopup.setOnHidden(event -> {
                            if (activePopup == event.getSource()) {
                                activePopup = null;
                                listView.getSelectionModel().clearSelection();
                            }
                        });
                    }
                }
            });
        }

        @Override
        protected void updateControl(ModListPageSkin.ModInfoObject dataItem, boolean empty) {
            if (empty || dataItem == null) return;

            LocalModFile modInfo = dataItem.getModInfo();
            ModTranslations.Mod modTranslations = dataItem.getModTranslations();
            ModLoaderType modLoaderType = modInfo.getModLoaderType();

            dataItem.loadIcon(imageView, new WeakReference<>(this.itemProperty()));

            String displayName = modInfo.getName();
            if (modTranslations != null && I18n.isUseChinese()) {
                String chineseName = modTranslations.getName();
                if (StringUtils.containsChinese(chineseName)) {
                    if (StringUtils.containsEmoji(chineseName)) {
                        StringBuilder builder = new StringBuilder();
                        chineseName.codePoints().forEach(ch -> {
                            if (ch < 0x1F300 || ch > 0x1FAFF) builder.appendCodePoint(ch);
                        });
                        chineseName = builder.toString().trim();
                    }
                    if (StringUtils.isNotBlank(chineseName) && !displayName.equalsIgnoreCase(chineseName)) {
                        displayName = displayName + " (" + chineseName + ")";
                    }
                }
            }
            content.setTitle(displayName);

            StringJoiner joiner = new StringJoiner(" | ");
            if (modLoaderType != ModLoaderType.UNKNOWN && StringUtils.isNotBlank(modInfo.getId()))
                joiner.add(modInfo.getId());
            joiner.add(org.jackhuang.hmcl.util.io.FileUtils.getName(modInfo.getFile()));
            content.setSubtitle(joiner.toString());

            content.getTags().clear();
            if (modInfo.hasBundledMods()) {
                content.addTag(i18n("mods.built_in") + ": " + modInfo.getBundledMods().size());
            }

            String modVersion = modInfo.getVersion();
            if (StringUtils.isNotBlank(modVersion) && !"${version}".equals(modVersion)) {
                content.addTag(modVersion);
            }
        }
    }

    private JFXPopup showBundledPopup(Node anchor, String modName, List<String> bundledMods) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setMaxHeight(400);
        root.getStyleClass().add("card-pane");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(i18n("mods.built_in") + " (" + bundledMods.size() + ")");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        JFXButton exportButton = new JFXButton();
        exportButton.setGraphic(FXUtils.limitingSize(SVG.FILE_EXPORT.createIcon(18), 18, 18));
        exportButton.getStyleClass().add("toggle-icon4");
        FXUtils.installFastTooltip(exportButton, i18n("mods.built_in.export.jij_info"));

        exportButton.setOnAction(e -> exportJijList(modName, bundledMods));

        header.getChildren().addAll(titleLabel, spacer, exportButton);

        JFXTextField searchField = new JFXTextField();
        searchField.setPromptText(i18n("mods.built_in.search"));
        searchField.setFocusTraversable(false);

        FlowPane flowPane = new FlowPane();
        flowPane.setHgap(8);
        flowPane.setVgap(8);
        flowPane.setPrefWrapLength(450);

        Runnable refreshList = () -> {
            flowPane.getChildren().clear();
            String query = searchField.getText().toLowerCase(Locale.ROOT);

            for (String path : bundledMods) {
                if (path.toLowerCase(Locale.ROOT).contains(query)) {
                    String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;

                    Label tag = new Label(name);
                    tag.setStyle("-fx-background-color: -fx-background; " +
                            "-fx-padding: 4 8; " +
                            "-fx-background-radius: 4; " +
                            "-fx-border-color: -fx-box-border; " +
                            "-fx-border-radius: 4;");
                    tag.setMaxWidth(430);
                    tag.setTooltip(new Tooltip(path));

                    flowPane.getChildren().add(tag);
                }
            }

            if (flowPane.getChildren().isEmpty()) {
                Label emptyLabel = new Label(i18n("mods.built_in.noresult"));
                emptyLabel.setStyle("-fx-text-fill: -fx-text-base-color-disabled;");
                flowPane.getChildren().add(emptyLabel);
            }
        };

        refreshList.run();
        searchField.textProperty().addListener((obs, oldVal, newVal) -> refreshList.run());

        ScrollPane scrollPane = new ScrollPane(flowPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        root.getChildren().addAll(header, searchField, scrollPane);

        JFXPopup popup = new JFXPopup(root);
        popup.show(anchor, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, anchor.getLayoutBounds().getHeight() + 5);
        return popup;
    }

    private static void exportJijList(String modName, List<String> bundledMods) {
        if (bundledMods == null || bundledMods.isEmpty()) return;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("mods.built_in.export.jij_info"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("extension.file"), "*.txt"));
        fileChooser.setInitialFileName(modName + "_JIJ_INFO.txt");

        File file = fileChooser.showSaveDialog(Controllers.getStage());

        if (file != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(modName).append(System.lineSeparator());

            for (String modPath : bundledMods) {
                String fileName = modPath.contains("/") ? modPath.substring(modPath.lastIndexOf('/') + 1) : modPath;
                sb.append("\t|->").append(fileName).append(System.lineSeparator());
            }

            Task.runAsync(() -> {
                try {
                    Files.writeString(file.toPath(), sb.toString());
                    LOG.info("Save to: " + file.getAbsolutePath());
                } catch (IOException ex) {
                    LOG.warning("Failed to export bundled mods list", ex);
                }
            }).start();
        }
    }

    private static void exportAllJijList(List<ModListPageSkin.ModInfoObject> allMods) {
        if (allMods == null || allMods.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        boolean hasData = false;

        for (ModListPageSkin.ModInfoObject item : allMods) {
            LocalModFile modInfo = item.getModInfo();
            if (modInfo == null) continue;
            List<String> bundledMods = modInfo.getBundledMods();

            if (bundledMods != null && !bundledMods.isEmpty()) {
                hasData = true;

                String displayName = modInfo.getName();
                if (item.getModTranslations() != null && I18n.isUseChinese()) {
                    String chineseName = item.getModTranslations().getName();
                    if (StringUtils.isNotBlank(chineseName)) {
                        displayName = displayName + " (" + chineseName + ")";
                    }
                }

                sb.append(displayName).append(System.lineSeparator());

                for (String modPath : bundledMods) {
                    String fileName = modPath.contains("/") ? modPath.substring(modPath.lastIndexOf('/') + 1) : modPath;
                    sb.append("\t|-> ").append(fileName).append(System.lineSeparator());
                }

                sb.append(System.lineSeparator());
            }
        }

        if (!hasData) {
            FXUtils.runInFX(() -> Controllers.dialog(i18n("mods.built_in.cancelexport")));
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("mods.built_in.export.jij_info.all"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("extension.file"), "*.txt"));
        fileChooser.setInitialFileName("ALL_JIJ_INFO.txt");

        File file = fileChooser.showSaveDialog(Controllers.getStage());

        if (file != null) {
            Task.runAsync(() -> {
                try {
                    Files.writeString(file.toPath(), sb.toString());
                    LOG.info("Save to: " + file.getAbsolutePath());
                } catch (IOException ex) {
                    LOG.warning("Failed to export all bundled mods list", ex);
                }
            }).start();
        }
    }
}
