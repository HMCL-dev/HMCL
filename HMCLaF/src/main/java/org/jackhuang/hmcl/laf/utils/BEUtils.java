/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEUtils.java at 2015-2-1 20:25:38, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.TexturePaint;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * The Class BEUtils.
 */
public class BEUtils {

    /**
     * 使用RescaleOp对图片进行滤镜处理.
     *
     * @param iconBottom 原图
     * @param redFilter 红色通道滤镜值，1.0f表示保持不变
     * @param greenFilter 绿色通道滤镜值，1.0f表示保持不变
     * @param blueFilter 蓝色通道滤镜值，1.0f表示保持不变
     * @param alphaFilter alpha通道滤镜值，1.0f表示保持不变
     * @return 处理后的图片新对象
     * @author Jack Jiang, 2013-04-05
     * @since 3.5
     */
    public static ImageIcon filterWithRescaleOp(ImageIcon iconBottom,
             float redFilter, float greenFilter, float blueFilter, float alphaFilter) {
        try {
            int w = iconBottom.getIconWidth(), h = iconBottom.getIconHeight();

            //原图
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gg = (Graphics2D) bi.getGraphics();
            gg.drawImage(iconBottom.getImage(), 0, 0, w, h, null);

            //设置滤镜效果
            float[] scales = { redFilter, greenFilter, blueFilter, alphaFilter };
            float[] offsets = new float[4];
            RescaleOp rop = new RescaleOp(scales, offsets, null);

            //执行
            //		gg.drawImage(bi, rop, 0, 0);//用这一行代码没效果，用下一行代码即可！
            rop.filter(bi, bi);
            return new ImageIcon(bi);

        } catch (Exception e) {
            LogHelper.error("filterWithRescaleOp出错了，" + e.getMessage() + ",iconBottom=" + iconBottom);
            return new ImageIcon();
        }
    }

    /**
     * <pre>
     * <b>给一个距形区域绘制4个角效果，形状和坐标如下：</b>
     * A(x,y)----B(x+β)                  C(x+(w-β),y)---D(x+w,y)
     * |                                                |
     * |                                                |
     * |                                                |
     * E(x,y+β)                                     L(x+w,y+β)
     *
     *
     * F(x,y+(h-β))                               K(x+w,y+(h-β))
     * |                                                |
     * |                                                |
     * |                                                |
     * G(x,y+h)----H(x+β,y+h)         I(x+(w-β),y+h)----J(x+w,y+h)
     * </pre>
     *
     * @param g
     * @param x 距形区的X坐标
     * @param y 距形区的Y坐标
     * @param w 距形区的宽
     * @param h 距形区的高
     * @param β 每个角的角长
     * @author Jack Jiang, 2013-04-05
     * @since 3.5
     */
    public static void draw4RecCorner(Graphics g, int x, int y, int w, int h, int β, Color c) {
        Color oldColor = g.getColor();

        g.setColor(c);
        //top(A~B,C~D)
        g.drawLine(x, y, x + β, y);
        g.drawLine(x + (w - β), y, x + w, y);

        //left(A~E,F~G)
        g.drawLine(x, y, x, y + β);
        g.drawLine(x, y + (h - β), x, y + h);

        //bottom(G~H,I~J)
        g.drawLine(x, y + h, x + β, y + h);
        g.drawLine(x + (w - β), y + h, x + w, y + h);

        //right(J~K,L~D)
        g.drawLine(x + w, y + h, x + w, y + (h - β));
        g.drawLine(x + w, y + β, x + w, y);
        g.setColor(oldColor);
    }

    /**
     * 设置对象集的透明性，如果该组件是Container及其子类则递归设
     * 置该组件内的所有子组件的透明性，直到组件中的任何组件都被设置完毕.
     *
     * @param comps 对象集
     * @param opaque true表示要设置成不透明，否则表示要设置成透明
     */
    public static void componentsOpaque(java.awt.Component[] comps,
             boolean opaque) {
        if (comps == null)
            return;
        for (Component c : comps)
            //递归设置它的子组件
            if (c instanceof Container) {
                if (c instanceof JComponent)
                    ((JComponent) c).setOpaque(opaque);
                componentsOpaque(((Container) c).getComponents(), opaque);
            } else
                if (c instanceof JComponent)
                    ((JComponent) c).setOpaque(opaque);
    }

    /**
     * 图形绘制反走样设置.
     *
     * @param g2 the g2
     * @param antiAliasing 是否反走样
     */
    public static void setAntiAliasing(Graphics2D g2, boolean antiAliasing) {
        if (antiAliasing)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                     RenderingHints.VALUE_ANTIALIAS_ON);
        else
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                     RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    /**
     * 填充3角形区域.
     *
     * @param g the g
     * @param x1 3个点之一的x坐标
     * @param y1 3个点之一的y坐标
     * @param x2 3个点之一的x坐标
     * @param y2 3个点之一的y坐标
     * @param x3 3个点之一的x坐标
     * @param y3 3个点之一的y坐标
     * @param c the c
     */
    public static void fillTriangle(Graphics g,
             int x1, int y1, int x2, int y2,
             int x3, int y3, Color c) {
        int[] x = new int[3], y = new int[3];
        // A simple triangle.
        x[0] = x1;
        x[1] = x2;
        x[2] = x3;
        y[0] = y1;
        y[1] = y2;
        y[2] = y3;
        int n = 3;

        Polygon p = new Polygon(x, y, n);  // This polygon represents a triangle with the above
        //   vertices.
        g.setColor(c);
        g.fillPolygon(p);     // Fills the triangle above.
    }

    /**
     * 绘制虚线框（本方法适用于对4个边的可选绘制情况下，可能会有4舍5入的小误差
     * ，除了要可选绘制4个边外，一般不推荐使用）.<br>.
     *
     * @param g the g
     * @param x the x
     * @param y the y
     * @param width the width
     * @param height the height
     */
    public static void drawDashedRect(Graphics g, int x, int y, int width, int height) {
        drawDashedRect(g, x, y, width, height, 6, 6, 2, 2);
    }

    /**
     * 绘制虚线框（本方法适用于对4个边的可选绘制情况下，可能会有4舍5入的小误差
     * ，除了要可选绘制4个边外，一般不推荐使用）.
     *
     * @param g the g
     * @param x the x
     * @param y the y
     * @param width the width
     * @param height the height
     * @param arcWidth the arc width
     * @param arcHeight the arc height
     * @param separator_solid 虚线段实线长度
     * @param separator_space 虚线段空白长度
     * add by js,2009-08-30
     */
    public static void drawDashedRect(Graphics g, int x, int y, int width, int height,
             int arcWidth, int arcHeight, int separator_solid, int separator_space) {
//    	drawDashedRect(g,x,y,width,height,step,true,true,true,true);
        BEUtils.setAntiAliasing((Graphics2D) g, true);

        //虚线样式
        Stroke oldStroke = ((Graphics2D) g).getStroke();
        Stroke sroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_BEVEL, 0, new float[] { separator_solid, separator_space }, 0);//实线，空白
        ((Graphics2D) g).setStroke(sroke);

        g.drawRoundRect(x, y,
                 width - 1, height - 1 //* 一个很诡异的问题：使用BasicStroke实现虚线绘制后，似乎绘制的距形
                //* 要比普通方法绘制实线距形往下偏移一个坐标，此处-1是为了修正这个问题，这难道是java的bug？
                //* 难怪当初打印工具开发时也遇到了莫名其妙偏移一个像素的现象，具体有待进一步研究！
                ,
                 arcWidth, arcHeight);

        ((Graphics2D) g).setStroke(oldStroke);
        BEUtils.setAntiAliasing((Graphics2D) g, false);
    }

    /**
     * Draw dashed rect.
     *
     * @param g the g
     * @param x the x
     * @param y the y
     * @param width the width
     * @param height the height
     * @param step the step
     * @param top the top
     * @param left the left
     * @param bottom the bottom
     * @param right the right
     */
    public static void drawDashedRect(Graphics g, int x, int y, int width, int height, int step//,boolean drawLeft$Right
            ,
             boolean top, boolean left, boolean bottom, boolean right) {
        int vx, vy;

        int drawStep = step == 0 ? 1 : 2 * step;
        int drawLingStep = step == 0 ? 1 : step;
        // draw upper and lower horizontal dashes
        for (vx = x; vx < (x + width); vx += drawStep) {
            if (top)
                g.fillRect(vx, y, drawLingStep, 1);
            if (bottom)
                g.fillRect(vx, y + height - 1, drawLingStep, 1);
        }

//    	if(drawLeft$Right)
        // draw left and right vertical dashes
        for (vy = y; vy < (y + height); vy += drawStep) {
            if (left)
                g.fillRect(x, vy, 1, drawLingStep);
            if (right)
                g.fillRect(x + width - 1, vy, 1, drawLingStep);
        }
    }

    /**
     * 对基准颜色的RGB通道进行加减后得到的新色调.
     *
     * @param basic 基准色调
     * @param r Red通道的增加值（可以是负）
     * @param g Geen通道的增加值（可以是负）
     * @param b Blue通道的增加值（可以是负）
     * @return the color
     */
    public static Color getColor(Color basic, int r, int g, int b) {
        return new Color(getColorInt(basic.getRed() + r),
                 getColorInt(basic.getGreen() + g),
                 getColorInt(basic.getBlue() + b),
                 getColorInt(basic.getAlpha()));
    }

    /**
     * 对基准颜色的RGBA通道进行加减后得到的新色调.
     *
     * @param basic 基准色调
     * @param r Red通道的增加值（可以是负）
     * @param g Geen通道的增加值（可以是负）
     * @param b Blue通道的增加值（可以是负）
     * @param a Alpha通道的增加值（可以是负）
     * @return the color
     */
    public static Color getColor(Color basic, int r, int g, int b, int a) {
        return new Color(getColorInt(basic.getRed() + r),
                 getColorInt(basic.getGreen() + g),
                 getColorInt(basic.getBlue() + b),
                 getColorInt(basic.getAlpha() + a));
    }

    /**
     * Gets the color int.
     *
     * @param rgb the rgb
     * @return the color int
     */
    public static int getColorInt(int rgb) {
        return rgb < 0 ? 0 : (rgb > 255 ? 255 : rgb);
    }

    /**
     * 获得字符串的像素宽度.
     *
     * @param fm the fm
     * @param str the str
     * @return the str pix width
     * @see FontMetrics#stringWidth(String)
     */
    public static int getStrPixWidth(FontMetrics fm, String str) {
        return fm.stringWidth(str + "");
    }

    /**
     * 获得字符串的像素宽度.
     *
     * @param f the f
     * @param str the str
     * @return the str pix width
     * @see #getStrPixWidth(FontMetrics, String)
     */
    public static int getStrPixWidth(Font f, String str) {
        return getStrPixWidth(Toolkit.getDefaultToolkit().getFontMetrics(f), str);
    }

    /**
     * 获得一个可按指定图片进行纹理填充方式的对象，利用此对象可实现图片填充背景效果。
     *
     * <pre>
     * 示例如下（在这个例子里将实现一个以指定图片填充效果为背景的面板对象）：
     * private FixedLayoutPane inputPane = new FixedLayoutPane() {
     * //给它弄一个图片平铺的背景
     * private TexturePaint paint = createTexturePaint(LaunchIconFactory.getInstance()
     * .getImage("/login_background.png").getImage());
     * //重写本方法实现图片平铺的背景
     * protected void paintComponent(Graphics g) {
     * super.paintComponent(g);
     * Graphics2D g2d = (Graphics2D) g;
     * g2d.setPaint(paint);
     * g2d.fillRect(0, 0, this.getWidth(), this.getHeight());
     * }
     * };
     * </pre>
     *
     * @param image 填充图片，该图片一宽1像素高N像素（据这1像素宽进行重复填充即可达到目的）
     * @return the texture paint
     */
    public static TexturePaint createTexturePaint(Image image) {
        int imageWidth = image.getWidth(null);
        int imageHeight = image.getHeight(null);
        BufferedImage bi = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bi.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        return new TexturePaint(bi, new Rectangle(0, 0, imageWidth, imageHeight));
    }

    /**
     * Gets the int.
     *
     * @param key the key
     * @param defaultValue the default value
     * @return the int
     */
    public static int getInt(Object key, int defaultValue) {
        Object value = UIManager.get(key);

        if (value instanceof Integer)
            return (Integer) value;
        if (value instanceof String)
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException nfe) {
            }
        return defaultValue;
    }

    /**
     * 在指定的区域内填充出厚重质感立体效果.
     *
     * @param g2 the g2
     * @param baseColor the base color
     * @param x the x
     * @param y the y
     * @param w the w
     * @param h the h
     * @param arc the arc
     */
    public static void fillTextureRoundRec(Graphics2D g2, Color baseColor,
             int x, int y, int w, int h, int arc) {
        fillTextureRoundRec(g2, baseColor,
                 x, y, w, h, arc, 35);
    }

    /**
     * 在指定的区域内填充出厚重质感立体效果.
     *
     * @param g2 the g2
     * @param baseColor the base color
     * @param x the x
     * @param y the y
     * @param w the w
     * @param h the h
     * @param arc the arc
     * @param colorDelta 渐变起色（上）与渐变止色（下）的RGB色差（矢量），正表示变淡，负表示加深
     */
    public static void fillTextureRoundRec(Graphics2D g2, Color baseColor,
             int x, int y, int w, int h, int arc, int colorDelta) {
        setAntiAliasing(g2, true);
        //矩形填充
        Paint oldpaint = g2.getPaint();
        GradientPaint gp = new GradientPaint(x, y, //渐变的起色比止色RGB浅35
                 getColor(baseColor, colorDelta, colorDelta, colorDelta),
                 x, y + h, baseColor);
        g2.setPaint(gp);
        g2.fillRoundRect(x, y, w, h, arc, arc);
        g2.setPaint(oldpaint);
        setAntiAliasing(g2, false);
    }
}
