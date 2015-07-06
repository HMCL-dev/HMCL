/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.settings;

/**
 *
 * @author huangyuhui
 */
public class Schedule {
    public static final int 
            TYPE_AUTO_SAVE = 0,
            TYPE_AUTO_RESTART = 1,
            TYPE_AUTO_BACKUP = 2,
            TYPE_AUTO_BROADCAST = 3,
            TYPE_AUTO_SEND_COMMAND = 4,
            TYPE_AUTO_EXECUTE = 5;
    public static final int
            TYPE2_AUTO_BACKUP_PLUGINS = 1,
            TYPE2_AUTH_BACKUP_CONFIG = 2,
            TYPE3_AUTH_BACKUP_WORLD = 3;
    public static final int
            TIME_TYPE_PER = 0,
            TIME_TYPE_PAST_HOUR = 1,
            TIME_TYPE_SERVER_STARTED = 2,
            TIME_TYPE_SERVER_STOPPED = 3,
            TIME_TYPE_SERVER_CRASHED = 4;
    
    public int type, type2, timeType;
    public String content;
    public double per;
}
