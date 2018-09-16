package org.jackhuang.hmcl.ui.versions;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.mod.Datapack;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.ListPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.MappedObservableList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class DatapackListPage extends ListPage<DatapackListItem> implements DecoratorPage {
    private final StringProperty title = new SimpleStringProperty();
    private final ObservableList<DatapackListItem> list; // Hold weak references
    private final Path worldDir;
    private final Datapack datapack;

    public DatapackListPage(String worldName, Path worldDir) {
        this.worldDir = worldDir;

        title.set(i18n("datapack.title", worldName));

        datapack = new Datapack(worldDir.resolve("datapacks"));
        datapack.loadFromDir();

        list = MappedObservableList.create(datapack.getInfo(), pack -> new DatapackListItem(pack, item -> {
            try {
                datapack.deletePack(pack.getId());
            } catch (IOException e) {
                Logging.LOG.warning("Failed to delete datapack");
            }
        }));
        itemsProperty().bindContent(list);

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
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("extension.datapack"), "*.zip"));
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
