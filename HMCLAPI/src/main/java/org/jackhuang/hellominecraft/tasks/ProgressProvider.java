/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.tasks;

/**
 *
 * @author hyh
 */
public abstract class ProgressProvider {
    protected ProgressProviderListener ppl;
    
    public ProgressProvider setProgressProviderListener(ProgressProviderListener p) {
        ppl = p;
        return this;
    }
}
