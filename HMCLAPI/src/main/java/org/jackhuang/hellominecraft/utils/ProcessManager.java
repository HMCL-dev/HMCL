/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

import java.util.HashSet;

/**
 *
 * @author huangyuhui
 */
public class ProcessManager {

    private static final HashSet<JavaProcess> gameProcesses = new HashSet();
    
    public void registerProcess(JavaProcess jp) {
        gameProcesses.add(jp);
    }
    
    public void stopAllProcesses() {
        for(JavaProcess jp : gameProcesses) {
            jp.stop();
        }
        gameProcesses.clear();
    }
    
    public void onProcessStopped(JavaProcess p) {
        gameProcesses.remove(p);
    }
}
