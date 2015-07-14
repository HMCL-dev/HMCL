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
package org.jackhuang.hellominecraft.svrmgr.settings;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * @author huangyuhui
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
