/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.svrmgr.settings;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * @author hyh
 */
public class BannedPlayers extends PlayerList<BannedPlayers.BannedPlayer> {

    @Override
    protected BannedPlayer newPlayer(String name) {
	return new BannedPlayer(name);
    }
    
    public static class BannedPlayer extends PlayerList.BasePlayer {

	public String source, expires, reason, created;
	
	public BannedPlayer(String name) {
	    super(name);
	    
	    created = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss +0800").format(new Date());
	    source = "Server";
	    expires = "forever";
	    reason = "你已经被服务器封禁";
	}
    }
}
