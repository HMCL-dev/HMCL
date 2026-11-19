// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.wizard.WizardSinglePage;
import org.jackhuang.hmcl.util.i18n.I18n;

import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class WorldExportPage extends WizardSinglePage {
    private final ObjectProperty<Path> path;
    private final StringProperty gameVersion = new SimpleStringProperty();
    private final StringProperty worldName = new SimpleStringProperty();
    private final World world;

    public WorldExportPage(World world, Path export, Runnable onFinish) {
        super(onFinish);

        this.world = world;

        path = new SimpleObjectProperty<>(export);
        if (world.getGameVersion() != null)
            gameVersion.set(I18n.getDisplayVersion(world.getGameVersion()));
        worldName.set(world.getWorldName());
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new WorldExportPageSkin(this);
    }

    public ObjectProperty<Path> pathProperty() {
        return path;
    }

    public StringProperty gameVersionProperty() {
        return gameVersion;
    }

    public StringProperty worldNameProperty() {
        return worldName;
    }

    public void export() {
        onFinish.run();
    }

    public  World getWorld() {
        return world;
    }

    @Override
    public String getTitle() {
        return i18n("world.export.wizard", world.getFileName());
    }

    @Override
    protected Object finish() {
        return Task.runAsync(i18n("world.export.wizard", worldName.get()), () -> world.export(path.get(), worldName.get()));
    }
}
