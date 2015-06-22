/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.svrmgr.settings;

/**
 *
 * @author hyh
 */
public class WhiteList extends PlayerList<WhiteList.WhiteListPlayer> {

    @Override
    protected WhiteList.WhiteListPlayer newPlayer(String name) {
	return new WhiteList.WhiteListPlayer(name);
    }
    
    public static class WhiteListPlayer extends PlayerList.BasePlayer {

	public WhiteListPlayer(String name) {
	    super(name);
	}
	
    }
    
}
