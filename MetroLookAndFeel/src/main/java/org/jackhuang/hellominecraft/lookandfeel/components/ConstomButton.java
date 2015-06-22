/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.lookandfeel.components;

import java.awt.Color;
import org.jackhuang.hellominecraft.lookandfeel.GraphicsUtils;

/**
 *
 * @author hyh
 */
public class ConstomButton extends javax.swing.JButton {
    public Color normalFg = GraphicsUtils.getWebColorWithAlpha("DDDDDD3F"), normalBg = GraphicsUtils.getWebColorWithAlpha("DDDDDD3F"),
	    prelightFg = GraphicsUtils.getWebColorWithAlpha("FFFFFF7F"), prelightBg = GraphicsUtils.getWebColorWithAlpha("FFFFFF7F"),
	    activeFg = GraphicsUtils.getWebColorWithAlpha("EAEDF83F"), activeBg = GraphicsUtils.getWebColorWithAlpha("EAEDF83F");
    public int drawPercent = 0;
    public long lastDrawTime = 0;
    public int radix = 0;
    public boolean notDraw = false;
}
