/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.utils.system;

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
