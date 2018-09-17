package org.jackhuang.hmcl.ui.versions;

import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.ListPage;
import org.jackhuang.hmcl.util.Logging;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class WorldListPage extends ListPage<WorldListItem> {
    private Path savesDir;

    public WorldListPage() {
    }

    public void loadVersion(Profile profile, String id) {
        this.savesDir = profile.getRepository().getRunDirectory(id).toPath().resolve("saves");

        itemsProperty().setAll(World.getWorlds(savesDir).stream()
                .map(WorldListItem::new).collect(Collectors.toList()));
    }

    @Override
    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("world.import.choose"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("world.extension"), "*.zip"));
        List<File> res = chooser.showOpenMultipleDialog(Controllers.getStage());

        if (res == null || res.isEmpty()) return;
        try {
            // Only accept one world file because user is required to confirm the new world name
            // Or too many input dialogs are popped.
            World world = new World(res.get(0).toPath());

            Controllers.inputDialog(i18n("world.name.enter"), (name, resolve, reject) -> {
                Task.of(() -> world.install(savesDir, name))
                        .finalized(Schedulers.javafx(), var -> {
                            itemsProperty().add(new WorldListItem(new World(savesDir.resolve(name))));
                            resolve.run();
                        }, e -> {
                            if (e instanceof FileAlreadyExistsException)
                                reject.accept(i18n("world.import.failed", i18n("world.import.already_exists")));
                            else
                                reject.accept(i18n("world.import.failed", e.getClass().getName() + ": " + e.getLocalizedMessage()));
                        }).start();
            }).setInitialText(world.getWorldName());

        } catch (IOException | IllegalArgumentException e) {
            Logging.LOG.log(Level.WARNING, "Unable to parse datapack file " + res.get(0), e);
        }
    }
}
