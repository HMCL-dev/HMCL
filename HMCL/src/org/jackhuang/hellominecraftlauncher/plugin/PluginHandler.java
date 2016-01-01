/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import org.jackhuang.hellominecraftlauncher.apis.IPluginHandler;
import org.jackhuang.hellominecraftlauncher.apis.IPluginRegister;
import org.jackhuang.hellominecraftlauncher.apis.IUIRegister;
import org.jackhuang.hellominecraftlauncher.apis.PluginHandlerType;

/**
 *
 * @author hyh
 */
public class PluginHandler implements IPluginRegister, IUIRegister {

    @Override
    public void registerPluginHandler(PluginHandlerType type, IPluginHandler handler) {
        if(!type.claSS.isInstance(handler)) {
            System.out.println("Error plugin handler, type: " + type.name);
            return;
        }
        if(!pluginHandlers.containsKey(type))
            pluginHandlers.put(type, new ArrayList<IPluginHandler>());
        pluginHandlers.get(type).add(handler);
    }
    
    private static Map<PluginHandlerType, List<IPluginHandler>> pluginHandlers;
    
    public static List<IPluginHandler> getPluginHandlers(PluginHandlerType type) {
        if(!pluginHandlers.containsKey(type))
            pluginHandlers.put(type, new ArrayList<IPluginHandler>());
        return pluginHandlers.get(type);
    }
    
    static {
        pluginHandlers = new HashMap<PluginHandlerType, List<IPluginHandler>>();
        mainPanels = new ArrayList<JPanel>();
        editPanels = new ArrayList<JPanel>();
        operationsMenuItems = new ArrayList<JMenuItem>();
        managingsMenuItems = new ArrayList<JMenuItem>();
        importingsMenuItems = new ArrayList<JMenuItem>();
    }

    @Override
    public void addPanelToMain(JPanel panel) {
        mainPanels.add(panel);
    }

    @Override
    public void addPanelToVersionEdit(JPanel panel) {
        editPanels.add(panel);
    }
    
    public static ArrayList<JPanel> mainPanels, editPanels;
    public static ArrayList<JMenuItem> operationsMenuItems, importingsMenuItems, managingsMenuItems;

    @Override
    public void addMenuItemToVersionOperations(JMenuItem menuItem) {
        operationsMenuItems.add(menuItem);
    }

    @Override
    public void addMenuItemToVersionImportings(JMenuItem menuItem) {
        importingsMenuItems.add(menuItem);
    }

    @Override
    public void addMenuItemToVersionManagings(JMenuItem menuItem) {
        managingsMenuItems.add(menuItem);
    }
    
}
