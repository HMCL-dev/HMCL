/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.mod.DataPack;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class DataPackListPage extends ListPageBase<DataPackListPageSkin.DataPackInfoObject> {
    private final Path worldDir;
    private final DataPack dataPack;
    private final Profile profile;
    private final String versionID;

    public DataPackListPage(WorldManagePage worldManagePage, Profile profile, String versionID) {
        this.worldDir = worldManagePage.getWorld().getFile();
        this.profile = profile;
        this.versionID = versionID;

        dataPack = new DataPack(worldDir.resolve("datapacks"));
        dataPack.loadFromDir();

        setItems(MappedObservableList.create(dataPack.getPacks(), DataPackListPageSkin.DataPackInfoObject::new));

        FXUtils.applyDragListener(this, it -> Objects.equals("zip", FileUtils.getExtension(it)),
                mods -> mods.forEach(this::installSingleDataPack), this::refresh);
    }

    public Profile getProfile() {
        return profile;
    }

    public String getVersionID() {
        return versionID;
    }

    private void installSingleDataPack(Path dataPack) {
        try {
            this.dataPack.installPack(dataPack);
        } catch (IOException | IllegalArgumentException e) {
            LOG.warning("Unable to parse datapack file " + dataPack, e);
        }
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new DataPackListPageSkin(this);
    }

    public void refresh() {
        setLoading(true);
        Task.runAsync(dataPack::loadFromDir)
                .withRunAsync(Schedulers.javafx(), () -> {
                    setLoading(false);
                })
                .start();
    }

    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("datapack.choose_datapack"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("datapack.extension"), "*.zip"));
        List<Path> res = FileUtils.toPaths(chooser.showOpenMultipleDialog(Controllers.getStage()));

        if (res != null) {
            res.forEach(this::installSingleDataPack);
        }

        dataPack.loadFromDir();
    }

    public void navigateToDownloadPage() {
        Controllers.getDownloadPage().showDatapackDownloads();
        Controllers.navigate(Controllers.getDownloadPage());
    }

    void removeSelected(ObservableList<DataPackListPageSkin.DataPackInfoObject> selectedItems) {
        selectedItems.stream()
                .map(DataPackListPageSkin.DataPackInfoObject::getPackInfo)
                .forEach(pack -> {
                    try {
                        dataPack.deletePack(pack);
                    } catch (IOException e) {
                        // Fail to remove mods if the game is running or the datapack is absent.
                        LOG.warning("Failed to delete datapack \"" + pack.getId() + "\"", e);
                    }
                });
    }

    void enableSelected(ObservableList<DataPackListPageSkin.DataPackInfoObject> selectedItems) {
        selectedItems.stream()
                .map(DataPackListPageSkin.DataPackInfoObject::getPackInfo)
                .forEach(pack -> pack.setActive(true));
    }

    void disableSelected(ObservableList<DataPackListPageSkin.DataPackInfoObject> selectedItems) {
        selectedItems.stream()
                .map(DataPackListPageSkin.DataPackInfoObject::getPackInfo)
                .forEach(pack -> pack.setActive(false));
    }

    void openDataPackFolder() {
        FXUtils.openFolder(dataPack.getPath());
    }

    @NotNull Predicate<DataPackListPageSkin.DataPackInfoObject> updateSearchPredicate(String queryString) {
        if (queryString.isBlank()) {
            return dataPack -> true;
        }

        final Predicate<String> stringPredicate;
        if (queryString.startsWith("regex:")) {
            try {
                Pattern pattern = Pattern.compile(StringUtils.substringAfter(queryString, "regex:"));
                stringPredicate = s -> s != null && pattern.matcher(s).find();
            } catch (Exception e) {
                return dataPack -> false;
            }
        } else {
            String lowerCaseFilter = queryString.toLowerCase(Locale.ROOT);
            stringPredicate = s -> s != null && s.toLowerCase(Locale.ROOT).contains(lowerCaseFilter);
        }

        return dataPack -> {
            String id = dataPack.getPackInfo().getId();
            String description = dataPack.getPackInfo().getDescription().toString();
            return stringPredicate.test(id) || stringPredicate.test(description);
        };
    }
}
