/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * beautyeye.java at 2015-2-1 20:25:40, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.resources;

import java.util.ListResourceBundle;

public final class beautyeye extends ListResourceBundle
{
	protected final Object[][] getContents()
	{
		return new Object[][] {
				{ "BETitlePane.setupButtonText", "Setup  " },
				{ "BETitlePane.titleMenuToolTipText", "Window operation." },
				{ "BETitlePane.closeButtonToolTipext", "Close" },
				{ "BETitlePane.iconifyButtonToolTipText", "Minimize" },
				{ "BETitlePane.toggleButtonToolTipText", "Maximize" },
				{ "BETitlePane.iconifyButtonText", "Minimize(N)" },
				{ "BETitlePane.restoreButtonText", "Restore(R)" },
				{ "BETitlePane.maximizeButtonText", "Maximized(X)" },
		};
	}
}
