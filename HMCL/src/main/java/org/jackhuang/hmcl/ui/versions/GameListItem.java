/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.versions;

import javafx.beans.property.*;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.Controllers;

import java.io.File;

import static org.jackhuang.hmcl.util.StringUtils.removePrefix;
import static org.jackhuang.hmcl.util.StringUtils.removeSuffix;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameListItem extends Control {
    private final Profile profile;
    private final String version;
    private final boolean isModpack;
    private final ToggleGroup toggleGroup;
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty subtitle = new SimpleStringProperty();
    private final BooleanProperty selected = new SimpleBooleanProperty();
    private final ObjectProperty<Image> image = new SimpleObjectProperty<>();

    public GameListItem(ToggleGroup toggleGroup, Profile profile, String id) {
        this.profile = profile;
        this.version = id;
        this.toggleGroup = toggleGroup;
        this.isModpack = profile.getRepository().isModpack(id);

        String game = GameVersion.minecraftVersion(profile.getRepository().getVersionJar(id)).orElse("Unknown");

        StringBuilder libraries = new StringBuilder(game);
        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(profile.getRepository().getVersion(id));
        analyzer.getForge().ifPresent(library -> libraries.append(", ").append(i18n("install.installer.forge")).append(": ").append(modifyVersion(game, library.getVersion().replaceAll("(?i)forge", ""))));
        analyzer.getLiteLoader().ifPresent(library -> libraries.append(", ").append(i18n("install.installer.liteloader")).append(": ").append(modifyVersion(game, library.getVersion().replaceAll("(?i)liteloader", ""))));
        analyzer.getOptiFine().ifPresent(library -> libraries.append(", ").append(i18n("install.installer.optifine")).append(": ").append(modifyVersion(game, library.getVersion().replaceAll("(?i)optifine", ""))));

        title.set(id);
        subtitle.set(libraries.toString());
        selected.set(profile.getSelectedVersion().equals(id));

        File iconFile = profile.getRepository().getVersionIcon(version);
        if (iconFile.exists())
            image.set(new Image("file:" + iconFile.getAbsolutePath()));
        else
            image.set(new Image("/assets/img/grass.png"));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new GameListItemSkin(this);
    }

    public ToggleGroup getToggleGroup() {
        return toggleGroup;
    }

    public Profile getProfile() {
        return profile;
    }

    public String getVersion() {
        return version;
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public ObjectProperty<Image> imageProperty() {
        return image;
    }

    public void checkSelection() {
        selected.set(version.equals(profile.getSelectedVersion()));
    }

    public void rename() {
        Versions.renameVersion(profile, version);
    }

    public void remove() {
        Versions.deleteVersion(profile, version);
    }

    public void export() {
        Versions.exportVersion(profile, version);
    }

    public void browse() {
        Versions.openFolder(profile, version);
    }

    public void launch() {
        Versions.launch(profile, version);
    }

    public void modifyGameSettings() {
        Controllers.getVersionPage().load(version, profile);
        Controllers.getDecorator().showPage(Controllers.getVersionPage());
    }

    public void generateLaunchScript() {
        Versions.generateLaunchScript(profile, version);
    }

    public boolean canUpdate() {
        return isModpack;
    }

    public void update() {
        Versions.updateVersion(profile, version);
    }

    private static String modifyVersion(String gameVersion, String version) {
        return removeSuffix(removePrefix(removeSuffix(removePrefix(version.replace(gameVersion, "").trim(), "-"), "-"), "_"), "_");
    }
}
