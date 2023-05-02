package org.jackhuang.hmcl.plugin.api;

import java.util.ArrayList;

@PluginAccessible
public final class PluginMainPageDesigner {
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

    @PluginAccessible
    public static final class PluginTextWidget implements IPluginWidget {
        private final String text;

        private PluginTextWidget(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    @PluginAccessible
    public static final class PluginLinebreakWidget implements IPluginWidget {
        private PluginLinebreakWidget() {
        }
    }

    @PluginAccessible
    public static final class PluginHorizontalSeparatorWidget implements IPluginWidget {
        private PluginHorizontalSeparatorWidget() {
        }
    }

    @PluginAccessible
    public static final class PluginVerticalSeparatorWidget implements IPluginWidget {
        private PluginVerticalSeparatorWidget() {
        }
    }

    private static final int MAX_WIDGET_SIZE = 128;

    private final ArrayList<IPluginWidget> widgets = new ArrayList<>();

    private boolean frozen = false;

    public ArrayList<IPluginWidget> getWidgets() {
        PluginUnsafeInterface.checkCallerClassPermission();

        return this.widgets;
    }

    public PluginMainPageDesigner freeze() {
        checkConditions();
        this.frozen = true;
        return this;
    }

    public boolean hasFrozen() {
        return this.frozen;
    }

    private void checkConditions() {
        if (this.widgets.size() == MAX_WIDGET_SIZE) {
            throw new RuntimeException("PluginMainPageDesigner contains too many widgets.");
        }
        if (this.frozen) {
            throw new RuntimeException("PluginMainPageDesigner has been frozen.");
        }
    }

    public PluginMainPageDesigner pushButton(String text, Runnable callback) {
        this.checkConditions();
        this.widgets.add(new PluginButtonWidget(text, callback));
        return this;
    }

    public PluginMainPageDesigner pushText(String text) {
        this.checkConditions();
        this.widgets.add(new PluginTextWidget(text));
        return this;
    }

    public PluginMainPageDesigner pushLinebreak() {
        this.checkConditions();
        this.widgets.add(new PluginLinebreakWidget());
        return this;
    }

    public PluginMainPageDesigner pushHorizontalSeparator() {
        this.checkConditions();
        this.widgets.add(new PluginHorizontalSeparatorWidget());
        return this;
    }

    public PluginMainPageDesigner pushVerticalSeparator() {
        this.checkConditions();
        this.widgets.add(new PluginVerticalSeparatorWidget());
        return this;
    }
}
