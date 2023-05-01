package org.jackhuang.hmcl.ui.plugin;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import javafx.scene.control.Control;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.ToolbarListPageSkin;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import java.util.Collections;
import java.util.List;

public class PluginsManagePage extends DecoratorAnimatedPage implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();

    public PluginsManagePage() {
        super();
        this.setCenter(new PluginList());
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    private class PluginList extends ListPageBase<PluginList.PluginListItem> {
        public PluginList() {
            super();
        }

        @Override
        protected PluginListSkin createDefaultSkin() {
            return new PluginListSkin();
        }

        private class PluginListSkin extends ToolbarListPageSkin<PluginList> {
            public PluginListSkin() {
                super(PluginList.this);
            }

            @Override
            protected List<Node> initializeToolbar(PluginList skinnable) {
                return Collections.emptyList();
            }
        }

        private class PluginListItem extends Control {

        }
    }
}
