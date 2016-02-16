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
package org.jackhuang.hellominecraft.util.ui;

import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.List;
import javax.swing.JFrame;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.logging.Level;

/**
 *
 * @author huangyuhui
 */
public class WebFrame extends JFrame {

    public WebFrame(String... strs) {
        this(("<html>" + StrUtils.parseParams(t -> ("<font color='#" + GraphicsUtils.getColor(Level.guessLevel((String) t, Level.INFO).COLOR) + "'>"), strs, t -> "</font><br />") + "</html>")
            .replace(" ", "&nbsp;").replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;"));
    }

    public WebFrame(String content) throws HeadlessException {
        addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) {
            }

            @Override
            public void windowClosing(WindowEvent e) {
                boolean flag = false;
                for (Frame f : Frame.getFrames()) {
                    if (f == WebFrame.this)
                        continue;
                    if (f.isVisible())
                        flag = true;
                }
                if (!flag)
                    System.exit(0);
            }

            @Override
            public void windowClosed(WindowEvent e) {
            }

            @Override
            public void windowIconified(WindowEvent e) {
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
            }

            @Override
            public void windowActivated(WindowEvent e) {
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
            }
        });

        add(new WebPage(content));
        pack();

        setLocationRelativeTo(null);
    }
}
