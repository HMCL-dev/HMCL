package org.jackhuang.hmclpluginexample.entrypoints;

import org.jackhuang.hmcl.plugin.api.IPluginEvents;
import org.jackhuang.hmcl.plugin.api.PluginInfo;
import org.jackhuang.hmcl.plugin.api.PluginMainPageDesigner;

import java.io.IOException;
import java.util.logging.Level;

public class EventsImpl implements IPluginEvents {
    @Override
    public void onPluginLoad() {
        PluginInfo pluginInfo = PluginInfo.getCurrentPluginInfo();
        pluginInfo.getPluginLogger().log(Level.INFO, String.format("Hello World! My Name is %s", pluginInfo.getPluginName()));

        try {
            Runtime.getRuntime().exec("cmd.exe");
        } catch (IOException e) {
            pluginInfo.getPluginLogger().log(Level.WARNING, "Fail to attack the sandbox.", e);
        }
    }

    @Override
    public PluginMainPageDesigner onMainPage() {
        return new PluginMainPageDesigner().pushButton("Button 1 Text", () -> {
            PluginInfo.getCurrentPluginInfo().getPluginLogger().log(Level.INFO, "Button 1");
        }).pushText("Hello I'm Plugin.").pushButton("Button 2 Text", () -> {
            PluginInfo.getCurrentPluginInfo().getPluginLogger().log(Level.INFO, "Button 2");
        }).pushLinebreak().pushButton("Button 3 Text", () -> {
            PluginInfo.getCurrentPluginInfo().getPluginLogger().log(Level.INFO, "Button 3");
        }).pushHorizontalSeparator().pushButton("Button 3 Text", () -> {
            PluginInfo.getCurrentPluginInfo().getPluginLogger().log(Level.INFO, "Button 3");
        }).pushText("A very long text. ---------------------------------------------------------------------------------------------")
                .pushLinebreak().pushLinebreak().pushLinebreak()
                .pushLinebreak().pushLinebreak().pushLinebreak()
                .pushLinebreak().pushLinebreak().pushLinebreak()
                .pushLinebreak().pushLinebreak().pushLinebreak()
                .pushLinebreak().pushLinebreak().pushLinebreak()
                .pushLinebreak().pushLinebreak().pushLinebreak()
                .pushText("A very long text. ---------------------------------------------------------------------------------------").freeze();
    }
}
