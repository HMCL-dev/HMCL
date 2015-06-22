/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.utils.functions.DoneListener0;
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
    public DoneListener0 dl;

    public UpdateChecker(VersionNumber base, String type, boolean continueUpdate, DoneListener0 dl) {
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
