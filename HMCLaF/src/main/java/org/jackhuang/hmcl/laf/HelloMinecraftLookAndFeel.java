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
package org.jackhuang.hmcl.laf;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.UIDefaults;
import javax.swing.plaf.synth.SynthLookAndFeel;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.util.sys.IOUtils;
import org.jackhuang.hmcl.util.ui.GraphicsUtils;

/**
 *
 * @author huangyuhui
 */
public class HelloMinecraftLookAndFeel extends SynthLookAndFeel {

    public static final Map<String, String> DEFAULT_SETTINGS = LAFTheme.BLUE.settings;

    /**
     * Creates a new instance of NimbusLookAndFeel
     *
     * @throws java.text.ParseException error parsing the xml, it must not
     * happen.
     */
    public HelloMinecraftLookAndFeel() throws ParseException {
        this(DEFAULT_SETTINGS);
    }

    public HelloMinecraftLookAndFeel(Map<String, String> settings) throws ParseException {
        try {
            try (InputStream is = HelloMinecraftLookAndFeel.class.getResourceAsStream("/org/jackhuang/hmcl/laf/synth.xml")) {
                String s = IOUtils.toString(is, "UTF-8");
                for (Map.Entry<String, String> ss : settings.entrySet())
                    s = s.replace("${" + ss.getKey() + "}", ss.getValue());
                load(new ByteArrayInputStream(s.getBytes("UTF-8")), HelloMinecraftLookAndFeel.class);
            }
        } catch (Throwable ex) {
            HMCLog.err("This fucking exception should not happen. Retry backup solution.", ex);
            try {
                try (InputStream is = HelloMinecraftLookAndFeel.class.getResourceAsStream("/org/jackhuang/hmcl/laf/synth_backup.xml")) {
                    load(is, HelloMinecraftLookAndFeel.class);
                }
            } catch (Throwable e) {
                HMCLog.err("User fault", e);
            }
        }
    }

    UIDefaults uiDefaults;

    @Override
    public UIDefaults getDefaults() {
        if (uiDefaults != null)
            return uiDefaults;
        uiDefaults = super.getDefaults();
        //ui.put("Table.selectionForeground", new ColorUIResource(Color.red));
        //ui.put("Table.focusCellForeground", new ColorUIResource(Color.red));
        //ui.put("TabbedPane.isTabRollover", false);
        //ui.put("ComboBox.selectionBackground", new ColorUIResource(Color.red));
        //ui.put("List.background", new ColorUIResource(Color.red));
        //uiDefaults.put("TabbedPane.selectedLabelShift", 0);
        uiDefaults.put("Table.selectionBackground", Color.red);
        return uiDefaults;
    }

    /**
     * Return a short string that identifies this look and feel.
     *
     * @return a short string identifying this look and feel.
     */
    @Override
    public String getName() {
        return "HelloMinecraftLookAndFeel";
    }

    /**
     * Return a string that identifies this look and feel.
     *
     * @return a short string identifying this look and feel.
     */
    @Override
    public String getID() {
        return "HelloMinecraftLookAndFeel";
    }

    /**
     * Returns a textual description of this look and feel.
     *
     * @return textual description of this look and feel.
     */
    @Override
    public String getDescription() {
        return "HelloMinecraftLookAndFeel";
    }

    /**
     * Load an image using ImageIO from resource in
     * org.jdesktop.swingx.plaf.nimbus.images. Catches and prints all Exceptions
     * so that it can safely be used in a static context.
     *
     * @param imgName The name of the image to load, eg. "border.png"
     *
     * @return The loaded image
     */
    public static BufferedImage loadImage(String imgName) {
        try {
            return ImageIO.read(GraphicsUtils.class.getClassLoader().getResource("org/jackhuang/hmcl/laf/images/" + imgName));
        } catch (Exception e) {
            System.err.println("Error loading image \"org/jackhuang/hmcl/laf/images/" + imgName + "\"");
            e.printStackTrace();
        }
        return null;
    }
}
