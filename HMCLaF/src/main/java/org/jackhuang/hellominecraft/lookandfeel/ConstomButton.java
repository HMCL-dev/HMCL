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
package org.jackhuang.hellominecraft.lookandfeel;

import java.awt.Color;
import org.jackhuang.hellominecraft.util.ui.GraphicsUtils;

/**
 *
 * @author huangyuhui
 */
public class ConstomButton extends javax.swing.JButton {
    public Color normalFg = GraphicsUtils.getWebColorWithAlpha("DDDDDD6F"), normalBg = GraphicsUtils.getWebColorWithAlpha("DDDDDD6F"),
	    prelightFg = GraphicsUtils.getWebColorWithAlpha("FFFFFF7F"), prelightBg = GraphicsUtils.getWebColorWithAlpha("FFFFFF7F"),
	    activeFg = GraphicsUtils.getWebColorWithAlpha("EAEDF83F"), activeBg = GraphicsUtils.getWebColorWithAlpha("EAEDF83F");
    public int drawPercent = 0;
    public int radix = 0;
}
