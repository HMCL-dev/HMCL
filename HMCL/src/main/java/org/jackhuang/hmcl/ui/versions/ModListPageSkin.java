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
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.CSVTable;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.logging.Level;
import org.jetbrains.annotations.NotNull;
import javafx.scene.Node;
import javafx.scene.control.SkinBase;

import static org.jackhuang.hmcl.ui.FXUtils.ignoreEvent;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.StringUtils.isNotBlank;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

class ModListPageSkin extends SkinBase<ModListPage> {
    private final ModListPage skinnable;
    private final TransitionPane toolbarPane;
    private final HBox searchBar;
    private final HBox toolbarNormal;
    private final HBox toolbarSelecting;

    private final JFXListView<ModInfoObject> listView;
    private final JFXTextField searchField;

    // FXThread
    private boolean isSearching = false;

    ModListPageSkin(ModListPage skinnable) {
        super(skinnable);
        this.skinnable = skinnable;

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
            toolbarSelecting = new HBox();

            // Search Bar
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
                        Bindings.bindContent(listView.getItems(), skinnable.getItems());
                    });

            onEscPressed(searchField, closeSearchBar::fire);

            searchBar.getChildren().setAll(searchField, closeSearchBar);

            // Toolbar Normal
            JFXButton menuButton = createToolbarButton2(i18n("button.more"), SVG.DOTS_HORIZONTAL, null);
            menuButton.setOnAction(e -> {
                menu.get().getContent().setAll(
                    new IconedMenuItem(SVG.UPDATE, i18n("mods.check_updates"), () -> skinnable.checkUpdates(), popup.get()),
                    new IconedMenuItem(SVG.EXPORT, i18n("button.export"), () -> exportList(), popup.get()),
                    new IconedMenuItem(SVG.ALERT, i18n("mods.check_duplicate_mods"), this::checkDuplicateModIds, popup.get())
                );
                popup.get().show(menuButton, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, menuButton.getHeight());
            });

            toolbarNormal.getChildren().setAll(
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                    createToolbarButton2(i18n("mods.add"), SVG.PLUS, skinnable::add),
                    createToolbarButton2(i18n("folder.mod"), SVG.FOLDER_OPEN, skinnable::openModFolder),
                    createToolbarButton2(i18n("download"), SVG.DOWNLOAD_OUTLINE, skinnable::download),
                    createToolbarButton2(i18n("search"), SVG.MAGNIFY, () -> changeToolbar(searchBar)),
                    menuButton
            );

            // Toolbar Selecting
            toolbarSelecting.getChildren().setAll(
                    createToolbarButton2(i18n("button.remove"), SVG.DELETE, () -> {
                        Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
                            skinnable.removeSelected(listView.getSelectionModel().getSelectedItems());
                        }, null);
                    }),
                    createToolbarButton2(i18n("mods.enable"), SVG.CHECK, () ->
                            skinnable.enableSelected(listView.getSelectionModel().getSelectedItems())),
                    createToolbarButton2(i18n("mods.disable"), SVG.CLOSE, () ->
                            skinnable.disableSelected(listView.getSelectionModel().getSelectedItems())),
                    createToolbarButton2(i18n("button.select_all"), SVG.SELECT_ALL, () ->
                            listView.getSelectionModel().selectAll()),
                    createToolbarButton2(i18n("button.cancel"), SVG.CANCEL, () ->
                            listView.getSelectionModel().clearSelection())
            );

            FXUtils.onChangeAndOperate(listView.getSelectionModel().selectedItemProperty(),
                    selectedItem -> {
                        if (selectedItem == null)
                            changeToolbar(isSearching ? searchBar : toolbarNormal);
                        else
                            changeToolbar(toolbarSelecting);
                    });
            root.getContent().add(toolbarPane);

            // Clear selection when pressing ESC
            root.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    if (listView.getSelectionModel().getSelectedItem() != null) {
                        listView.getSelectionModel().clearSelection();
                        e.consume();
                    }
                }
            });
        }

        {
            SpinnerPane center = new SpinnerPane();
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.getStyleClass().add("large-spinner-pane");
            center.loadingProperty().bind(skinnable.loadingProperty());

            Holder<Object> lastCell = new Holder<>();
            listView.setCellFactory(x -> new ModInfoListCell(listView, lastCell));
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            Bindings.bindContent(listView.getItems(), skinnable.getItems());
            skinnable.getItems().addListener((ListChangeListener<? super ModInfoObject>) c -> {
                if (isSearching) {
                    search();
                }
            });

            listView.setOnContextMenuRequested(event -> {
                ModInfoObject selectedItem = listView.getSelectionModel().getSelectedItem();
                if (selectedItem != null && listView.getSelectionModel().getSelectedItems().size() == 1) {
                    listView.getSelectionModel().clearSelection();
                    Controllers.dialog(new ModInfoDialog(selectedItem));
                }
            });

            // ListViewBehavior would consume ESC pressed event, preventing us from handling it
            // So we ignore it here
            ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

            center.setContent(listView);
            root.getContent().add(center);
        }

        Label label = new Label(i18n("mods.not_modded"));
        label.prefWidthProperty().bind(pane.widthProperty().add(-100));

        FXUtils.onChangeAndOperate(skinnable.moddedProperty(), modded -> {
            if (modded) pane.getChildren().setAll(root);
            else pane.getChildren().setAll(label);
        });

        getChildren().setAll(pane);
    }

    private void exportList() {
        Path exportPath = Paths.get("hmcl-mod-list-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")) + ".csv").toAbsolutePath();

        Controllers.taskDialog(Task.runAsync(() -> {
            CSVTable modDataTable = CSVTable.createEmpty();

            // Headers grouped by data type and source
            String[] columnHeaders = {
                // Local Basic Info
                "fileName", "name", "modID", "version", "modLoader", "homePageURL", "authors", "logoPath",
                
                // Local HMCL Info
                "displayName", "abbr", "mcmodID", "subName", "Curseforge",
                
                // Local File Info 
                "status", "path", "SHA-1",
                
                // Remote Common Info
                "remoteLoaderTypes",
                
                // Remote CurseForge Info
                "remoteCurseForgeID",
                "curseforgeDependencies", 
                "curseforgeGameVersions",
                "curseforgeVersionType",
                
                // Remote Modrinth Info 
                "remoteModrinthID",
                "modrinthDependencies",
                "modrinthGameVersions", 
                "modrinthVersionType"
            };

            // Initialize headers
            for (int i = 0; i < columnHeaders.length; i++) {
                modDataTable.set(i, 0, columnHeaders[i]);
            }

            // Future collections for async operations
            // Local computations
            List<CompletableFuture<String>> fileHashFutures = new ArrayList<>();
            
            // Common remote data
            List<CompletableFuture<List<ModLoaderType>>> modLoadersFutures = new ArrayList<>();
            
            // CurseForge remote data
            List<CompletableFuture<String>> curseForgeIdFutures = new ArrayList<>();
            List<CompletableFuture<String>> curseForgeDependenciesFutures = new ArrayList<>();
            List<CompletableFuture<List<String>>> curseForgeGameVersionsFutures = new ArrayList<>();
            List<CompletableFuture<String>> curseForgeVersionTypeFutures = new ArrayList<>();
            
            // Modrinth remote data
            List<CompletableFuture<String>> modrinthIdFutures = new ArrayList<>();
            List<CompletableFuture<String>> modrinthDependenciesFutures = new ArrayList<>();
            List<CompletableFuture<List<String>>> modrinthGameVersionsFutures = new ArrayList<>();
            List<CompletableFuture<String>> modrinthVersionTypeFutures = new ArrayList<>();

            List<ModInfoObject> modList = listView.getItems();

            // Collect data for each mod
            for (int i = 0; i < modList.size(); i++) {
                ModInfoObject mod = modList.get(i);
                int rowIndex = i + 1;

                // Write local basic data
                modDataTable.set(0, rowIndex, FileUtils.getName(mod.getModInfo().getFile()));
                modDataTable.set(1, rowIndex, mod.getModInfo().getName());
                modDataTable.set(2, rowIndex, mod.getModInfo().getId());
                modDataTable.set(3, rowIndex, mod.getModInfo().getVersion());
                modDataTable.set(4, rowIndex, mod.getModInfo().getModLoaderType().name());
                modDataTable.set(5, rowIndex, mod.getModInfo().getUrl());
                modDataTable.set(6, rowIndex, mod.getModInfo().getAuthors());
                modDataTable.set(7, rowIndex, mod.getModInfo().getLogoPath());
                if (mod.getMod() != null) {
                    modDataTable.set(8, rowIndex, mod.getMod().getDisplayName());
                    modDataTable.set(9, rowIndex, mod.getMod().getAbbr());
                    modDataTable.set(10, rowIndex, mod.getMod().getMcmod());
                    modDataTable.set(11, rowIndex, mod.getMod().getSubname());
                    modDataTable.set(12, rowIndex, mod.getMod().getCurseforge());
                }
                modDataTable.set(13, rowIndex, mod.getModInfo().getFile().toString().endsWith(".disabled") ? "Disabled" : "Enabled");
                modDataTable.set(14, rowIndex, mod.getModInfo().getFile().toString());

                // Initialize async operations
                fileHashFutures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return DigestUtils.digestToString("SHA-1", mod.getModInfo().getFile());
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, "Failed to calculate SHA-1", e);
                        return "";
                    }
                }));

                modLoadersFutures.add(CompletableFuture.supplyAsync(() -> {
                    Set<ModLoaderType> loaders = new LinkedHashSet<>();
                    
                    try {
                        CurseForgeRemoteModRepository.MODS.getRemoteVersionByLocalFile(mod.getModInfo(), mod.getModInfo().getFile())
                                .ifPresent(version -> loaders.addAll(version.getLoaders()));
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Failed to get CurseForge loader types", e);
                    }
                    
                    try {
                        ModrinthRemoteModRepository.MODS.getRemoteVersionByLocalFile(mod.getModInfo(), mod.getModInfo().getFile())
                                .ifPresent(version -> loaders.addAll(version.getLoaders()));
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Failed to get Modrinth loader types", e);
                    }
                    
                    return new ArrayList<>(loaders);
                }));

                // Initialize CurseForge data futures
                curseForgeIdFutures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return CurseForgeRemoteModRepository.MODS.getRemoteVersionByLocalFile(mod.getModInfo(), mod.getModInfo().getFile())
                                .map(version -> version.getModid())
                                .orElse("");
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Failed to get CurseForge ID", e);
                        return "";
                    }
                }));

                curseForgeDependenciesFutures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return CurseForgeRemoteModRepository.MODS.getRemoteVersionByLocalFile(mod.getModInfo(), mod.getModInfo().getFile())
                                .map(version -> version.getDependencies().stream()
                                        .map(dep -> dep.getType() + ": " + dep.getId())
                                        .collect(Collectors.joining(", ")))
                                .orElse("");
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Failed to get CurseForge dependencies", e);
                        return "";
                    }
                }));

                curseForgeGameVersionsFutures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return CurseForgeRemoteModRepository.MODS.getRemoteVersionByLocalFile(mod.getModInfo(), mod.getModInfo().getFile())
                                .map(version -> version.getGameVersions())
                                .orElse(Collections.emptyList());
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Failed to get CurseForge game versions", e);
                        return Collections.emptyList();
                    }
                }));

                curseForgeVersionTypeFutures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return CurseForgeRemoteModRepository.MODS.getRemoteVersionByLocalFile(mod.getModInfo(), mod.getModInfo().getFile())
                                .map(version -> version.getVersionType().name())
                                .orElse("");
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Failed to get CurseForge version type", e);
                        return "";
                    }
                }));

                // Initialize Modrinth data futures  
                modrinthIdFutures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return ModrinthRemoteModRepository.MODS.getRemoteVersionByLocalFile(mod.getModInfo(), mod.getModInfo().getFile())
                                .map(version -> version.getModid())
                                .orElse("");
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Failed to get Modrinth ID", e);
                        return "";
                    }
                }));

                modrinthDependenciesFutures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return ModrinthRemoteModRepository.MODS.getRemoteVersionByLocalFile(mod.getModInfo(), mod.getModInfo().getFile())
                                .map(version -> version.getDependencies().stream()
                                        .map(dep -> dep.getType() + ": " + dep.getId())
                                        .collect(Collectors.joining(", ")))
                                .orElse("");
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Failed to get Modrinth dependencies", e);
                        return "";
                    }
                }));

                modrinthGameVersionsFutures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return ModrinthRemoteModRepository.MODS.getRemoteVersionByLocalFile(mod.getModInfo(), mod.getModInfo().getFile())
                                .map(version -> version.getGameVersions())
                                .orElse(Collections.emptyList());
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Failed to get Modrinth game versions", e);
                        return Collections.emptyList();
                    }
                }));

                modrinthVersionTypeFutures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return ModrinthRemoteModRepository.MODS.getRemoteVersionByLocalFile(mod.getModInfo(), mod.getModInfo().getFile())
                                .map(version -> version.getVersionType().name())
                                .orElse("");
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Failed to get Modrinth version type", e);
                        return "";
                    }
                }));
            }

            // Collect results
            List<String> fileHashes = fileHashFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            List<List<ModLoaderType>> modLoaders = modLoadersFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            // CurseForge results
            List<String> curseForgeIds = curseForgeIdFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
            List<String> curseForgeDependencies = curseForgeDependenciesFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
            List<List<String>> curseForgeGameVersions = curseForgeGameVersionsFutures.stream()
                    .map(CompletableFuture::join) 
                    .collect(Collectors.toList());
            List<String> curseForgeVersionTypes = curseForgeVersionTypeFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            // Modrinth results
            List<String> modrinthIds = modrinthIdFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
            List<String> modrinthDependencies = modrinthDependenciesFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
            List<List<String>> modrinthGameVersions = modrinthGameVersionsFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
            List<String> modrinthVersionTypes = modrinthVersionTypeFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            // Write collected data to CSV
            for (int i = 0; i < modList.size(); i++) {
                int rowIndex = i + 1;
                
                // Write file hash
                modDataTable.set(15, rowIndex, fileHashes.get(i));
                
                // Write mod loaders
                modDataTable.set(16, rowIndex, modLoaders.get(i).stream()
                        .map(ModLoaderType::name)
                        .collect(Collectors.joining(", ")));
                        
                // Write remote data
                modDataTable.set(17, rowIndex, curseForgeIds.get(i));
                modDataTable.set(18, rowIndex, modrinthIds.get(i));
                modDataTable.set(19, rowIndex, curseForgeDependencies.get(i));
                modDataTable.set(20, rowIndex, modrinthDependencies.get(i));
                modDataTable.set(21, rowIndex, String.join(", ", curseForgeGameVersions.get(i)));
                modDataTable.set(22, rowIndex, String.join(", ", modrinthGameVersions.get(i)));
                modDataTable.set(23, rowIndex, curseForgeVersionTypes.get(i));
                modDataTable.set(24, rowIndex, modrinthVersionTypes.get(i));
            }

            // Save CSV file
            try (OutputStream outputStream = Files.newOutputStream(exportPath)) {
                modDataTable.write(outputStream);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to write CSV file", e);
            }

            FXUtils.showFileInExplorer(exportPath);
        }).whenComplete(Schedulers.javafx(), exception -> {
            if (exception == null) {
                Controllers.dialog(exportPath.toString(), i18n("message.success"));
            } else {
                Controllers.dialog(exception.toString(), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            }
        }), i18n("button.export"), TaskCancellationAction.NO_CANCEL);
    }

    private void changeToolbar(HBox newToolbar) {
        Node oldToolbar = toolbarPane.getCurrentNode();
        if (newToolbar != oldToolbar) {
            toolbarPane.setContent(newToolbar, ContainerAnimations.FADE);
            if (newToolbar == searchBar) {
                searchField.requestFocus();
            }
        }
    }

    private void search() {
        isSearching = true;

        Bindings.unbindContent(listView.getItems(), skinnable.getItems());

        String queryString = searchField.getText();
        if (StringUtils.isBlank(queryString)) {
            listView.getItems().setAll(skinnable.getItems());
        } else {
            listView.getItems().clear();

            Predicate<String> predicate;
            if (queryString.startsWith("regex:")) {
                try {
                    Pattern pattern = Pattern.compile(queryString.substring("regex:".length()));
                    predicate = s -> pattern.matcher(s).find();
                } catch (Throwable e) {
                    LOG.warning("Illegal regular expression", e);
                    return;
                }
            } else {
                String lowerQueryString = queryString.toLowerCase(Locale.ROOT);
                predicate = s -> s.toLowerCase(Locale.ROOT).contains(lowerQueryString);
            }

            // Do we need to search in the background thread?
            for (ModInfoObject item : skinnable.getItems()) {
                if (predicate.test(item.getModInfo().getFileName() +
                        item.getModInfo().getName() +
                        item.getModInfo().getVersion() +
                        item.getModInfo().getGameVersion() +
                        item.getModInfo().getId() +
                        item.getModInfo().getModLoaderType() +
                        (item.getMod() != null ? item.getMod().getDisplayName() : ""))) {
                    listView.getItems().add(item);
                }
            }
        }
    }

    private void checkDuplicateModIds() {
        Map<String, List<String>> modIdMap = new HashMap<>();
        for (ModInfoObject modInfo : skinnable.getItems()) {
            if (modInfo.getModInfo().getFile().toString().endsWith(".disabled")) {
                continue;
            }
            String modId = modInfo.getModInfo().getId();
            String fileName = modInfo.getModInfo().getFileName();
            modIdMap.computeIfAbsent(modId, k -> new ArrayList<>()).add(fileName);
        }

        List<String> duplicateMods = modIdMap.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(entry -> "Mod ID: " + entry.getKey() + "\nFiles: " + String.join(", ", entry.getValue()))
            .collect(Collectors.toList());

        if (duplicateMods.isEmpty()) {
            Controllers.dialog(i18n("mods.check_duplicate_mods.empty"), i18n("message.info"));
        } else {
            String duplicateInfo = String.join("\n---\n", duplicateMods);
            JFXButton deleteButton = new JFXButton(i18n("button.copy"));
            deleteButton.getStyleClass().add("dialog-info");
            deleteButton.setOnAction(e -> FXUtils.copyText(duplicateInfo));
            Controllers.confirmAction(duplicateInfo, i18n("mods.check_duplicate_mods"), MessageDialogPane.MessageType.INFO, deleteButton);
        }
    }

    private static Task<Image> loadModIcon(LocalModFile modFile, int size) {
        return Task.supplyAsync(() -> {
            if (StringUtils.isNotBlank(modFile.getLogoPath())) {
                try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(modFile.getFile())) {
                    Path iconPath = fs.getPath(modFile.getLogoPath());
                    if (Files.exists(iconPath)) {
                        try (InputStream stream = Files.newInputStream(iconPath)) {
                            Image image = new Image(stream, size, size, true, true);
                            if (!image.isError() && image.getWidth() == image.getHeight())
                                return image;
                        }
                    }
                } catch (Exception e) {
                    LOG.warning("Failed to load image " + modFile.getLogoPath(), e);
                }
            }

            try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(modFile.getFile())) {
                List<String> defaultPaths = new ArrayList<>(Arrays.asList(
                        "icon.png",
                        "logo.png", 
                        "mod_logo.png",
                        "pack.png",
                        "logoFile.png"
                ));

                String id = modFile.getId();
                if (StringUtils.isNotBlank(id)) {
                    defaultPaths.addAll(Arrays.asList(
                            "assets/" + id + "/icon.png",
                            "assets/" + id.replace("-", "") + "/icon.png", 
                            id + ".png",
                            id + "-logo.png",
                            id + "-icon.png",
                            id + "_logo.png",
                            id + "_icon.png"
                    ));
                }

                for (String path : defaultPaths) {
                    Path iconPath = fs.getPath(path);
                    if (Files.exists(iconPath)) {
                        try (InputStream stream = Files.newInputStream(iconPath)) {
                            Image image = new Image(stream, size, size, true, true);
                            if (!image.isError() && image.getWidth() == image.getHeight())
                                return image;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warning("Failed to load icon", e);
            }

            String iconPath;
            switch (modFile.getModLoaderType()) {
                case FORGE: iconPath = "/assets/img/forge.png"; break;
                case NEO_FORGED: iconPath = "/assets/img/neoforge.png"; break; 
                case FABRIC: iconPath = "/assets/img/fabric.png"; break;
                case QUILT: iconPath = "/assets/img/quilt.png"; break;
                case LITE_LOADER: iconPath = "/assets/img/liteloader.png"; break;
                default: iconPath = "/assets/img/command.png"; break;
            }
            return FXUtils.newBuiltinImage(iconPath, size, size, true, true);
        });
    }

    static class ModInfoObject extends RecursiveTreeObject<ModInfoObject> implements Comparable<ModInfoObject> {
        private final BooleanProperty active;
        private final LocalModFile localModFile;
        private final String title;
        private final String message;
        private final ModTranslations.Mod mod;

        ModInfoObject(LocalModFile localModFile) {
            this.localModFile = localModFile;
            this.active = localModFile.activeProperty();

            StringBuilder title = new StringBuilder(localModFile.getName());
            this.title = title.toString();

            List<String> parts = new ArrayList<>();
            if (isNotBlank(localModFile.getId())) {
                parts.add(localModFile.getId());
            }
            if (isNotBlank(localModFile.getVersion())) {
                parts.add(localModFile.getVersion());
            }
            if (isNotBlank(localModFile.getGameVersion())) {
                parts.add(i18n("game.version") + ": " + localModFile.getGameVersion());
            }
            String message = String.join(", ", parts);
            this.message = message.toString();

            this.mod = ModTranslations.MOD.getModById(localModFile.getId());
        }

        String getTitle() {
            return title;
        }

        String getSubtitle() {
            return message;
        }

        LocalModFile getModInfo() {
            return localModFile;
        }

        public ModTranslations.Mod getMod() {
            return mod;
        }

        @Override
        public int compareTo(@NotNull ModListPageSkin.ModInfoObject o) {
            return localModFile.getFileName().toLowerCase().compareTo(o.localModFile.getFileName().toLowerCase());
        }
    }

    class ModInfoDialog extends JFXDialogLayout {

        ModInfoDialog(ModInfoObject modInfo) {
            HBox titleContainer = new HBox();
            titleContainer.setSpacing(8);

            ImageView imageView = new ImageView(); 
            loadModIcon(modInfo.getModInfo(), 40)
                .whenComplete(Schedulers.javafx(), (image, exception) -> {
                    imageView.setImage(image);
                }).start();

            TwoLineListItem title = new TwoLineListItem();
            title.setTitle(modInfo.getModInfo().getName());
            if (modInfo.getMod() != null) {
                title.getTags().add(modInfo.getMod().getDisplayName());
            }
            if (StringUtils.isNotBlank(modInfo.getModInfo().getGameVersion())) {
                title.getTags().add(i18n("game.version") + ": " + modInfo.getModInfo().getGameVersion());
            }
            if (StringUtils.isNotBlank(modInfo.getModInfo().getVersion())) {
                title.getTags().add(modInfo.getModInfo().getVersion());
            }
            if (StringUtils.isNotBlank(modInfo.getModInfo().getAuthors())) {
                title.getTags().add(i18n("archive.author") + ": " + modInfo.getModInfo().getAuthors());
            }
            title.setSubtitle(FileUtils.getName(modInfo.getModInfo().getFile()));

            titleContainer.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), title);
            setHeading(titleContainer);

            Label description = new Label(modInfo.getModInfo().getDescription().toString());
            setBody(description);

            if (StringUtils.isNotBlank(modInfo.getModInfo().getId())) {
                for (Pair<String, ? extends RemoteModRepository> item : Arrays.asList(
                        pair("mods.curseforge", CurseForgeRemoteModRepository.MODS),
                        pair("mods.modrinth", ModrinthRemoteModRepository.MODS)
                )) {
                    RemoteModRepository repository = item.getValue();
                    JFXHyperlink button = new JFXHyperlink(i18n(item.getKey()));
                    Task.runAsync(() -> {
                        Optional<RemoteMod.Version> versionOptional = repository.getRemoteVersionByLocalFile(modInfo.getModInfo(), modInfo.getModInfo().getFile());
                        if (versionOptional.isPresent()) {
                            RemoteMod remoteMod = repository.getModById(versionOptional.get().getModid());
                            FXUtils.runInFX(() -> {
                                for (ModLoaderType modLoaderType : versionOptional.get().getLoaders()) {
                                    String loaderName;
                                    switch (modLoaderType) {
                                        case FORGE:
                                            loaderName = i18n("install.installer.forge");
                                            break;
                                        case NEO_FORGED:
                                            loaderName = i18n("install.installer.neoforge");
                                            break;
                                        case FABRIC:
                                            loaderName = i18n("install.installer.fabric");
                                            break;
                                        case LITE_LOADER:
                                            loaderName = i18n("install.installer.liteloader");
                                            break;
                                        case QUILT:
                                            loaderName = i18n("install.installer.quilt");
                                            break;
                                        default:
                                            continue;
                                    }
                                    List<String> tags = title.getTags();
                                    if (!tags.contains(loaderName)) {
                                        tags.add(loaderName);
                                    }
                                }

                                button.setOnAction(e -> {
                                    fireEvent(new DialogCloseEvent());
                                    Controllers.navigate(new DownloadPage(
                                            repository instanceof CurseForgeRemoteModRepository ? HMCLLocalizedDownloadListPage.ofCurseForgeMod(null, false) : HMCLLocalizedDownloadListPage.ofModrinthMod(null, false),
                                            remoteMod,
                                            new Profile.ProfileVersion(skinnable.getProfile(), skinnable.getVersionId()),
                                            null
                                    ));
                                });
                                button.setDisable(false);
                            });
                        }
                    }).start();
                    button.setDisable(true);
                    getActions().add(button);
                }
            }

            if (StringUtils.isNotBlank(modInfo.getModInfo().getUrl())) {
                JFXHyperlink officialPageButton = new JFXHyperlink(i18n("mods.url"));
                officialPageButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(modInfo.getModInfo().getUrl());
                });

                getActions().add(officialPageButton);
            }

            if (modInfo.getMod() == null || StringUtils.isBlank(modInfo.getMod().getMcmod())) {
                JFXHyperlink searchButton = new JFXHyperlink(i18n("mods.mcmod.search"));
                searchButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(NetworkUtils.withQuery("https://search.mcmod.cn/s", mapOf(
                            pair("key", modInfo.getModInfo().getName()),
                            pair("site", "all"),
                            pair("filter", "0")
                    )));
                });
                getActions().add(searchButton);
            } else {
                JFXHyperlink mcmodButton = new JFXHyperlink(i18n("mods.mcmod.page"));
                mcmodButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(ModTranslations.MOD.getMcmodUrl(modInfo.getMod()));
                });
                getActions().add(mcmodButton);
            }

            JFXButton okButton = new JFXButton();
            okButton.getStyleClass().add("dialog-accept");
            okButton.setText(i18n("button.ok"));
            okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            getActions().add(okButton);

            onEscPressed(this, okButton::fire);
        }
    }

    private static final Lazy<PopupMenu> menu = new Lazy<>(PopupMenu::new);
    private static final Lazy<JFXPopup> popup = new Lazy<>(() -> new JFXPopup(menu.get()));

    final class ModInfoListCell extends MDListCell<ModInfoObject> {
        JFXCheckBox checkBox = new JFXCheckBox();
        ImageView imageView = new ImageView();
        TwoLineListItem content = new TwoLineListItem();
        JFXButton restoreButton = new JFXButton();
        JFXButton infoButton = new JFXButton();
        JFXButton revealButton = new JFXButton();
        BooleanProperty booleanProperty;

        ModInfoListCell(JFXListView<ModInfoObject> listView, Holder<Object> lastCell) {
            super(listView, lastCell);

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);
            setSelectable();

            imageView.setFitWidth(24);
            imageView.setFitHeight(24);
            imageView.setPreserveRatio(true);
            imageView.setImage(FXUtils.newBuiltinImage("/assets/img/command.png", 24, 24, true, true));

            restoreButton.getStyleClass().add("toggle-icon4");
            restoreButton.setGraphic(FXUtils.limitingSize(SVG.RESTORE.createIcon(Theme.blackFill(), 24, 24), 24, 24));

            FXUtils.installFastTooltip(restoreButton, i18n("mods.restore"));

            revealButton.getStyleClass().add("toggle-icon4");
            revealButton.setGraphic(FXUtils.limitingSize(SVG.FOLDER_OUTLINE.createIcon(Theme.blackFill(), 24, 24), 24, 24));

            infoButton.getStyleClass().add("toggle-icon4");
            infoButton.setGraphic(FXUtils.limitingSize(SVG.INFORMATION_OUTLINE.createIcon(Theme.blackFill(), 24, 24), 24, 24));

            container.getChildren().setAll(checkBox, imageView, content, restoreButton, revealButton, infoButton);

            StackPane.setMargin(container, new Insets(8));
            getContainer().getChildren().setAll(container);
        }

        @Override
        protected void updateControl(ModInfoObject dataItem, boolean empty) {
            if (empty) return;
  
            loadModIcon(dataItem.getModInfo(), 24)
                .whenComplete(Schedulers.javafx(), (image, exception) -> {
                    imageView.setImage(image);
                }).start();
            
            content.setTitle(dataItem.getTitle());
            content.getTags().clear();
            switch (dataItem.getModInfo().getModLoaderType()) {
                case FORGE:
                    content.getTags().add(i18n("install.installer.forge"));
                    break;
                case NEO_FORGED:
                    content.getTags().add(i18n("install.installer.neoforge"));
                    break;
                case FABRIC:
                    content.getTags().add(i18n("install.installer.fabric"));
                    break;
                case LITE_LOADER:
                    content.getTags().add(i18n("install.installer.liteloader"));
                    break;
                case QUILT:
                    content.getTags().add(i18n("install.installer.quilt"));
                    break;
            }
            if (dataItem.getMod() != null && I18n.isUseChinese()) {
                if (isNotBlank(dataItem.getSubtitle())) {
                    content.setSubtitle(dataItem.getSubtitle() + ", " + dataItem.getMod().getDisplayName());
                } else {
                    content.setSubtitle(dataItem.getMod().getDisplayName());
                }
            } else {
                content.setSubtitle(dataItem.getSubtitle());
            }
            if (booleanProperty != null) {
                checkBox.selectedProperty().unbindBidirectional(booleanProperty);
            }
            checkBox.selectedProperty().bindBidirectional(booleanProperty = dataItem.active);
            restoreButton.setVisible(!dataItem.getModInfo().getMod().getOldFiles().isEmpty());
            restoreButton.setOnAction(e -> {
                menu.get().getContent().setAll(dataItem.getModInfo().getMod().getOldFiles().stream()
                        .map(localModFile -> new IconedMenuItem(null, localModFile.getVersion(),
                                () -> skinnable.rollback(dataItem.getModInfo(), localModFile),
                                popup.get()))
                        .collect(Collectors.toList())
                );

                popup.get().show(restoreButton, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, restoreButton.getHeight());
            });
            revealButton.setOnAction(e -> FXUtils.showFileInExplorer(dataItem.getModInfo().getFile()));
            infoButton.setOnAction(e -> Controllers.dialog(new ModInfoDialog(dataItem)));
        }
    }
}
