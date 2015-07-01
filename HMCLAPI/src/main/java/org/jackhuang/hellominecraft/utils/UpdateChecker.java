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
package org.jackhuang.hellominecraft.utils;

import org.jackhuang.hellominecraft.utils.system.MessageBox;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.utils.functions.NonConsumer;
import org.jackhuang.hellominecraft.HMCLog;

/**
 *
 * @author hyh
 */
public final class UpdateChecker extends Thread {
    public static boolean OUT_DATED = false;
    public VersionNumber base;
    public String type;
    public boolean continueUpdate;
    public NonConsumer dl;

    public UpdateChecker(VersionNumber base, String type, boolean continueUpdate, NonConsumer dl) {
	super("UpdateChecker");
        this.base = base;
        this.type = type;
        this.continueUpdate = continueUpdate;
        this.dl = dl;
    }
    
    VersionNumber value;

    @Override
    public void run() {

	String url = "http://huangyuhui.duapp.com/info.php?type=" + type, version;
	try {
	    version = NetUtils.doGet(url);
	} catch (Exception e) {
            HMCLog.warn("Failed to get update url.", e);
	    return;
	}
	value = VersionNumber.check(version);
	if (!continueUpdate) {
	    return;
	}
        process(false);
    }
    
    public void process(boolean showMessage) {
	if (value == null) {
	    HMCLog.warn("Failed to check update...");
            if(showMessage) {
                MessageBox.Show(C.i18n("update.failed"));
            }
	} else {
	    if (VersionNumber.isOlder(base, value)) {
                OUT_DATED = true;
		dl.onDone();
	    }
	}
    }
    
    public VersionNumber getNewVersion() {
        return value;
    }

}
