package org.jackhuang.hmcl.ui.versions;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.mod.curse.CurseAddon;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

public class ModDownloadPage extends Control implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final CurseAddon addon;
    private final Version version;

    public ModDownloadPage(CurseAddon addon, Version version) {
        this.addon = addon;
        this.version = version;
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModDownloadPageSkin(this);
    }

    private static class ModDownloadPageSkin extends SkinBase<ModDownloadPage> {

        protected ModDownloadPageSkin(ModDownloadPage control) {
            super(control);
        }
    }
}
