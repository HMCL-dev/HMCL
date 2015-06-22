/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.tasks;

import java.util.Collection;
import java.util.HashSet;
import org.jackhuang.hellominecraft.tasks.Task;

/**
 *
 * @author huangyuhui
 */
public class ParallelTask extends Task {
    Collection<Task> dependsTask = new HashSet<Task>();

    @Override
    public boolean executeTask() {
        return true;
    }

    @Override
    public String getInfo() {
        return "PARALLEL";
    }

    @Override
    public Collection<Task> getDependTasks() {
        return dependsTask;
    }
    
    public void addDependsTask(Task t) {
        dependsTask.add(t);
    }
    
}
