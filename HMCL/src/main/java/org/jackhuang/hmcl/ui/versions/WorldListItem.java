package org.jackhuang.hmcl.ui.versions;

import javafx.beans.property.*;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.wizard.SinglePageWizardProvider;
import org.jackhuang.hmcl.util.IntVersionNumber;
import org.jackhuang.hmcl.util.VersionNumber;

import java.io.File;
import java.util.Date;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class WorldListItem extends Control {
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty subtitle = new SimpleStringProperty();
    private final ObjectProperty<Image> image = new SimpleObjectProperty<>();
    private final World world;

    public WorldListItem(World world) {
        this.world = world;

        title.set(world.getWorldName());
        subtitle.set(i18n("world.description", world.getFileName(), new Date(world.getLastPlayed()).toString(), world.getGameVersion() == null ? i18n("message.unknown") : world.getGameVersion()));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new WorldListItemSkin(this);
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    public ObjectProperty<Image> imageProperty() {
        return image;
    }

    public void export() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("world.export.title"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("world"), "*.zip"));
        fileChooser.setInitialFileName(world.getWorldName());
        File file = fileChooser.showSaveDialog(Controllers.getStage());
        if (file == null) {
            return;
        }

        Controllers.getDecorator().startWizard(new SinglePageWizardProvider(controller -> new WorldExportPage(world, file.toPath(), controller::onFinish)));
    }

    public void manageDatapacks() {
        if (world.getGameVersion() == null || // old game will not write game version to level.dat
                (IntVersionNumber.isIntVersionNumber(world.getGameVersion()) // we don't parse snapshot version
                        && VersionNumber.asVersion(world.getGameVersion()).compareTo(VersionNumber.asVersion("1.13")) < 0)) {
            Controllers.dialog(i18n("world.datapack.1_13"));
            return;
        }
        Controllers.navigate(new DatapackListPage(world.getWorldName(), world.getFile()));
    }
}
