/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.tasks;

/**
 *
 * @author huangyuhui
 */
public class DoubleTask extends Task {
    
    Task a, b;

    public DoubleTask(Task a, Task b) {
        this.a = a;
        this.b = b;
    }
    
    @Override
    public void executeTask() throws Throwable {
        a.executeTask(); b.executeTask();
    }

    @Override
    public String getInfo() {
        return "Double Task";
    }
    
}
