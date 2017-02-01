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
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Map;
import javax.swing.UIDefaults;
import javax.swing.plaf.synth.SynthLookAndFeel;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.sys.IOUtils;

/**
 *
 * @author huangyuhui
 */
public class HelloMinecraftLookAndFeel extends SynthLookAndFeel {

    public static final Map<String, String> DEFAULT_SETTINGS = Theme.BLUE.settings;

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
            try (InputStream is = HelloMinecraftLookAndFeel.class.getResourceAsStream("/org/jackhuang/hellominecraft/lookandfeel/synth.xml")) {
                String s = IOUtils.toString(is, "UTF-8");
                for (Map.Entry<String, String> ss : settings.entrySet())
                    s = s.replace("${" + ss.getKey() + "}", ss.getValue());
                load(new ByteArrayInputStream(s.getBytes("UTF-8")), HelloMinecraftLookAndFeel.class);
            }
        } catch (Throwable ex) {
            HMCLog.err("This fucking exception should not happen. Retry backup solution.", ex);
            try {
                try (InputStream is = HelloMinecraftLookAndFeel.class.getResourceAsStream("/org/jackhuang/hellominecraft/lookandfeel/synth_backup.xml")) {
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
}
