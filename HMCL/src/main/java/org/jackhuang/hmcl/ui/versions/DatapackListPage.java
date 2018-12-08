/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.mod.Datapack;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.ListPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class DatapackListPage extends ListPage<DatapackListItem> implements DecoratorPage {
    private final StringProperty title = new SimpleStringProperty();
    private final Path worldDir;
    private final Datapack datapack;

    public DatapackListPage(String worldName, Path worldDir) {
        this.worldDir = worldDir;

        title.set(i18n("datapack.title", worldName));

        datapack = new Datapack(worldDir.resolve("datapacks"));
        datapack.loadFromDir();

        setItems(MappedObservableList.create(datapack.getInfo(),
                pack -> new DatapackListItem(pack, item -> {
                    try {
                        datapack.deletePack(pack);
                    } catch (IOException e) {
                        Logging.LOG.warning("Failed to delete datapack");
                    }
                })));

        setOnDragOver(event -> {
            if (event.getGestureSource() != this && event.getDragboard().hasFiles())
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            event.consume();
        });

        setOnDragDropped(event -> {
            List<File> files = event.getDragboard().getFiles();
            if (files != null) {
                Collection<File> mods = files.stream()
                        .filter(it -> Objects.equals("zip", FileUtils.getExtension(it)))
                        .collect(Collectors.toList());
                if (!mods.isEmpty()) {
                    mods.forEach(it -> {
                        try {
                            Datapack zip = new Datapack(it.toPath());
                            zip.loadFromZip();
                            zip.installTo(worldDir);
                        } catch (IOException | IllegalArgumentException e) {
                            Logging.LOG.log(Level.WARNING, "Unable to parse datapack file " + it, e);
                        }
                    });
                    event.setDropCompleted(true);
                }
            }
            datapack.loadFromDir();
            event.consume();
        });
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }

    @Override
    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("datapack.choose_datapack"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("datapack.extension"), "*.zip"));
        List<File> res = chooser.showOpenMultipleDialog(Controllers.getStage());

        if (res != null)
            res.forEach(it -> {
                try {
                    Datapack zip = new Datapack(it.toPath());
                    zip.loadFromZip();
                    zip.installTo(worldDir);
                } catch (IOException | IllegalArgumentException e) {
                    Logging.LOG.log(Level.WARNING, "Unable to parse datapack file " + it, e);
                }
            });

        datapack.loadFromDir();
    }
}
