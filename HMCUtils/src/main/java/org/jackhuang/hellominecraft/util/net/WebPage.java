/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hellominecraft.util.net;

import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.ui.SwingUtils;

/**
 *
 * @author huangyuhui
 */
public class WebPage extends JScrollPane {

    private final javax.swing.JTextPane browser;

    /**
     * Creates new form WebPagePanel
     */
    public WebPage(String content) {
        browser = new javax.swing.JTextPane();
        browser.setEditable(false);
        browser.setMargin(null);
        browser.setContentType("text/html");
        browser.addHyperlinkListener(he -> {
            if (he.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
                try {
                    SwingUtils.openLink(he.getURL().toString());
                } catch (Exception e) {
                    HMCLog.err("Unexpected exception opening link " + he.getURL(), e);
                }
        });
        browser.setText(content);

        setViewportView(browser);
    }
}
