/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
    public String getDescription() {
        return "HelloMinecraftLookAndFeel";
    }
}
