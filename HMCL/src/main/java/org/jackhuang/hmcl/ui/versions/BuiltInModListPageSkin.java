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
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.setting.VersionIconType;
import org.jackhuang.hmcl.util.i18n.I18n;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.util.List;
import java.util.StringJoiner;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class BuiltInModListPageSkin extends SkinBase<BuiltInModListPage> {

    private final JFXListView<ModListPageSkin.ModInfoObject> listView;
    private final HBox toolbar;

    protected BuiltInModListPageSkin(BuiltInModListPage skinnable) {
        super(skinnable);

        StackPane rootPane = new StackPane();
        rootPane.setPadding(new Insets(10));
        rootPane.getStyleClass().add("notice-pane");

        VBox contentBox = new VBox();
        contentBox.setSpacing(10);

        listView = new JFXListView<>();
        toolbar = new HBox();
        toolbar.getChildren().addAll(
                createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                createToolbarButton2("导出全部 JIJ 信息", SVG.DOWNLOAD, () -> exportAllJijList(listView.getItems())),
                createToolbarButton2(i18n("search"), SVG.SEARCH, () -> {
                    // 搜索功能预留
                })
        );

        listView.setItems(skinnable.getItems());

        VBox.setVgrow(listView, Priority.ALWAYS);

        listView.setCellFactory(param -> new JijModListCell(listView));

        contentBox.getChildren().addAll(toolbar, listView);
        rootPane.getChildren().add(contentBox);

        getChildren().add(rootPane);
    }

    private class JijModListCell extends MDListCell<ModListPageSkin.ModInfoObject> {

        private final ImageView imageView = new ImageView();
        private final TwoLineListItem content = new TwoLineListItem();
        private final HBox container = new HBox(8);

        public JijModListCell(JFXListView<ModListPageSkin.ModInfoObject> listView) {
            super(listView);
            this.getStyleClass().add("mod-info-list-cell");

            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            StackPane.setMargin(container, new Insets(8));

            imageView.setFitWidth(24);
            imageView.setFitHeight(24);
            imageView.setPreserveRatio(true);

            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);

            container.getChildren().addAll(imageView, content);
            getContainer().getChildren().setAll(container);

            setSelectable();

            this.setOnMouseClicked(e -> {
                if (getItem() != null && getItem().getModInfo() != null) {
                    LocalModFile modFile = getItem().getModInfo();
                    if (modFile.hasBundledMods()) {
                        showBundledPopup(this, modFile.getName(), modFile.getBundledMods());
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
                content.addTag("内置: " + modInfo.getBundledMods().size());
            }

            String modVersion = modInfo.getVersion();
            if (StringUtils.isNotBlank(modVersion) && !"${version}".equals(modVersion)) {
                content.addTag(modVersion);
            }
        }
    }

    private void showBundledPopup(Node anchor, String modName, List<String> bundledMods) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setMaxHeight(400);
        root.setStyle("-fx-background-color: -fx-background;");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("内置模组 (" + bundledMods.size() + ")");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        JFXButton exportButton = new JFXButton();
        exportButton.setGraphic(FXUtils.limitingSize(SVG.DOWNLOAD.createIcon(18), 18, 18));
        exportButton.getStyleClass().add("toggle-icon4");
        FXUtils.installFastTooltip(exportButton, "导出JIJ信息");

        exportButton.setOnAction(e -> exportJijList(modName, bundledMods));

        header.getChildren().addAll(titleLabel, spacer, exportButton);

        JFXTextField searchField = new JFXTextField();
        searchField.setPromptText("搜索内置模组...");
        searchField.setFocusTraversable(false);

        FlowPane flowPane = new FlowPane();
        flowPane.setHgap(8);
        flowPane.setVgap(8);
        flowPane.setPrefWrapLength(450);

        Runnable refreshList = () -> {
            flowPane.getChildren().clear();
            String query = searchField.getText().toLowerCase();

            for (String path : bundledMods) {
                if (path.toLowerCase().contains(query)) {
                    String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;

                    Label tag = new Label(name);
                    tag.setStyle("-fx-background-color: -fx-control-inner-background-alt; " +
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
                Label emptyLabel = new Label("无匹配结果");
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
    }

    private static void exportJijList(String modName, List<String> bundledMods) {
        if (bundledMods == null || bundledMods.isEmpty()) return;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("保存内置模组列表");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("文本文件 (*.txt)", "*.txt"));
        fileChooser.setInitialFileName("JIJ.txt");

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
                    LOG.info("导出成功: " + file.getAbsolutePath());
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
            FXUtils.runInFX(() -> Controllers.confirm("无包含JIJ信息的模组，操作将取消", i18n("button.ok"), () -> {}, null));
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("导出所有内置模组信息");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("文本文件 (*.txt)", "*.txt"));
        fileChooser.setInitialFileName("ALL_JIJ_INFO.txt");

        File file = fileChooser.showSaveDialog(Controllers.getStage());

        if (file != null) {
            Task.runAsync(() -> {
                try {
                    Files.writeString(file.toPath(), sb.toString());
                    LOG.info("全部导出成功: " + file.getAbsolutePath());
                } catch (IOException ex) {
                    LOG.warning("Failed to export all bundled mods list", ex);
                }
            }).start();
        }
    }
}
