package org.jackhuang.hmcl.plugin.api;

import java.util.ArrayList;

@PluginAccessible
public final class PluginMainPageDesigner {
    public ArrayList<IPluginWidget> getWidgets() {
        PluginUnsafeInterface.checkCallerClassPermission();

        return this.widgets;
    }

    @PluginAccessible
    public interface IPluginWidget {
    }

    @PluginAccessible
    public static final class PluginButtonWidget implements IPluginWidget {
        private final Runnable onClick;

        private final String text;

        private PluginButtonWidget(String text, Runnable onClick) {
            this.text = text;
            this.onClick = onClick;
        }

        public String getText() {
            return this.text;
        }

        public void onClick() {
            this.onClick.run();
        }
    }

    private static final int MAX_WIDGET_SIZE = 4;

    private final ArrayList<IPluginWidget> widgets = new ArrayList<>();

    public PluginMainPageDesigner pushButton(String text, Runnable callback) {
        if (widgets.size() == MAX_WIDGET_SIZE) {
            throw new RuntimeException("Too many widgets.");
        }
        widgets.add(new PluginButtonWidget(text, callback));
        return this;
    }
}
