/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.installer.cauldron;

import java.io.File;

/**
 *
 * @author hyh
 */
public class MinecraftLibrary {

    public String url, formatted=null, name;
    //public boolean serverreq=true, clientreq=true;
    public String[] checksums;

    public void init() {
	String str = name;
	String[] s = str.split(":");
	str = s[0];
	str = str.replace('.', File.separatorChar);
	str += File.separator + s[1] + File.separator + s[2]
		+ File.separator + s[1] + '-' + s[2] + ".jar";
	formatted = str;
    }
}
