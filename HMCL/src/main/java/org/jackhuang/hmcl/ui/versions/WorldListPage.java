package org.jackhuang.hmcl.ui.versions;

import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.ListPage;
import org.jackhuang.hmcl.util.Logging;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class WorldListPage extends ListPage<WorldListItem> {
    private Path savesDir;

    public WorldListPage() {
    }

    public void loadVersion(Profile profile, String id) {
        this.savesDir = profile.getRepository().getRunDirectory(id).toPath().resolve("saves");

        itemsProperty().clear();
        try {
            if (Files.exists(savesDir))
                for (Path worldDir : Files.newDirectoryStream(savesDir)) {
                    itemsProperty().add(new WorldListItem(new World(worldDir)));
                }
        } catch (IOException e) {
            Logging.LOG.log(Level.WARNING, "Failed to read saves", e);
        }
    }

    @Override
    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("world.choose_world"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("world.extension"), "*.zip"));
        List<File> res = chooser.showOpenMultipleDialog(Controllers.getStage());

        if (res == null) return;
        res.forEach(it -> {
            try {
                World world = new World(it.toPath());
                world.install(savesDir, world.getWorldName());
                itemsProperty().add(new WorldListItem(new World(savesDir.resolve(world.getWorldName()))));
            } catch (IOException | IllegalArgumentException e) {
                Logging.LOG.log(Level.WARNING, "Unable to parse datapack file " + it, e);
            }
        });
    }
}
