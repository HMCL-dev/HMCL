/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils.functions;

/**
 *
 * @author hyh
 */
public class TrueDoneListener implements DoneListener0Return<Boolean> {

    public static final TrueDoneListener instance = new TrueDoneListener();
    
    private TrueDoneListener(){}
    
    @Override
    public Boolean onDone() {
        return Boolean.TRUE;
    }
    
}
