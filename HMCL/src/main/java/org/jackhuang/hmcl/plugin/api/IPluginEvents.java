package org.jackhuang.hmcl.plugin.api;

@PluginAccessible
public interface IPluginEvents {
    void onPluginLoad();

    PluginMainPageDesigner onMainPage();
}
