/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraftlauncher.apis;

import java.lang.reflect.Method;

/**
 *
 * @author hyh
 */
public class ApplicationManager {
    
    public static String getTitle() {
        try {
            Class MainClass = Class.forName("org.jackhuang.hellominecraftlauncher.Main");
            try {
                Method makeTitle = MainClass.getMethod("makeTitle", new Class[] {});
                return (String) makeTitle.invoke(MainClass, new Object[]{});
            } catch(Exception e) {
                HMCLLog.warn("Cannot get title for plugins because method 'makeTitle' has thrown some exceptions.", e);
            }
        } catch(Exception e) {
            HMCLLog.warn("Cannot get title for plugins because class 'Main' cannot be found.", e);
        }
        return "Hello Minecraft! Launcher";
    }
    
}
