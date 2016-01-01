/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis;

import javax.swing.JMenuItem;
import javax.swing.JPanel;

/**
 *
 * @author hyh
 */
public interface IUIRegister {
    
    /**
     * 添加选项卡到首页选项卡集合中
     * @param panel 选项卡标题为panel.getName()
     */
    void addPanelToMain(JPanel panel);
    
    /**
     * 添加JPanel到版本设置选项卡集合中
     * @param panel 选项卡标题为panel.getName()
     */
    void addPanelToVersionEdit(JPanel panel);
    
    void addMenuItemToVersionOperations(JMenuItem menuItem);
    void addMenuItemToVersionImportings(JMenuItem menuItem);
    void addMenuItemToVersionManagings(JMenuItem menuItem);
    
}
