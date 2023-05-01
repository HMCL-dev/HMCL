package org.jackhuang.hmclpluginexample.entrypoints;

import org.jackhuang.hmcl.plugin.api.IPluginEvents;
import org.jackhuang.hmcl.plugin.api.PluginInfo;
import org.jackhuang.hmcl.plugin.api.PluginMainPageDesigner;

import java.util.logging.Level;

public class EventsImpl implements IPluginEvents {
    @Override
    public void onPluginLoad() {
        PluginInfo pluginInfo = PluginInfo.getCurrentPluginInfo();
        pluginInfo.getPluginLogger().log(Level.INFO, String.format("Hello World! My Name is %s", pluginInfo.getPluginName()));
    }

    @Override
    public PluginMainPageDesigner onMainPage() {
        return new PluginMainPageDesigner().pushButton("Button 1 Text", () -> {
            PluginInfo.getCurrentPluginInfo().getPluginLogger().log(Level.INFO, "Button 1");
        });
    }
}
