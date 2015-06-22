/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.tasks;

import java.util.ArrayList;
import org.jackhuang.hellominecraft.tasks.communication.PreviousResult;
import org.jackhuang.hellominecraft.tasks.communication.PreviousResultRegistrator;
import org.jackhuang.hellominecraft.utils.functions.Consumer;

/**
 *
 * @author hyh
 * @param <T> Runnable&lt;T&gt;
 */
public class TaskRunnableArg1<T> extends TaskInfo implements PreviousResultRegistrator<T> {
    private final Consumer<T> r;
    public TaskRunnableArg1(String info, Consumer<T> r) {
        super(info);
        this.r = r;
    }

    @Override
    public boolean executeTask() {
        if(al.size() != 1) throw new IllegalStateException("the count of args is not one.");
        try {
            r.accept(al.get(0).getResult());
            return true;
        } catch(Throwable t) {
            setFailReason(t);
            return false;
        }
    }
    
    ArrayList<PreviousResult<T>> al = new ArrayList();

    @Override
    public Task registerPreviousResult(PreviousResult<T> pr) {
        al.add(pr);
        return this;
    }
    
    
    
}