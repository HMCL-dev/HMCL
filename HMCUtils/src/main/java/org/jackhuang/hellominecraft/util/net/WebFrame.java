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

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JDialog;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.log.Level;
import org.jackhuang.hellominecraft.util.ui.GraphicsUtils;
import org.jackhuang.hellominecraft.util.ui.SwingUtils;

/**
 *
 * @author huangyuhui
 */
public class WebFrame extends JDialog {

    public WebFrame(String... strs) {
        this(("<html>" + StrUtils.parseParams(t -> ("<font color='#" + GraphicsUtils.getColor(Level.guessLevel((String) t, Level.INFO).COLOR) + "'>"), strs, t -> "</font><br />") + "</html>")
            .replace(" ", "&nbsp;").replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;"));
    }

    public WebFrame(String content) {
        super((JDialog) null, false);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                SwingUtils.exitIfNoWindow(WebFrame.this);
            }
        });

        add(new WebPage(content));
        pack();

        setLocationRelativeTo(null);
    }
}
