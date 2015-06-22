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

import java.text.ParseException;
import javax.swing.plaf.synth.SynthLookAndFeel;

/**
 *
 * @author hyh
 */
public class HelloMinecraftLookAndFeel extends SynthLookAndFeel {

    /**
     * Creates a new instance of NimbusLookAndFeel
     * @throws java.text.ParseException error parsing the xml, it must not happen.
     */
    public HelloMinecraftLookAndFeel() throws ParseException {
        load(HelloMinecraftLookAndFeel.class.getResourceAsStream("/org/jackhuang/hellominecraft/lookandfeel/synth.xml"), HelloMinecraftLookAndFeel.class);
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
