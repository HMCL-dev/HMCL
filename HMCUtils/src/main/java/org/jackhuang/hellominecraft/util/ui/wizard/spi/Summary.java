/*  The contents of this file are subject to the terms of the Common Development
and Distribution License (the License). You may not use this file except in
compliance with the License.
    You can obtain a copy of the License at http://www.netbeans.org/cddl.html
or http://www.netbeans.org/cddl.txt.
    When distributing Covered Code, include this CDDL Header Notice in each file
and include the License file at http://www.netbeans.org/cddl.txt.
If applicable, add the following below the CDDL Header, with the fields
enclosed by brackets [] replaced by your own identifying information:
"Portions Copyrighted [year] [name of copyright owner]" */
 /*
 * Summary.java
 *
 * Created on September 24, 2006, 4:05 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.util.ui.wizard.spi;

import java.awt.Component;
import java.awt.Font;
import java.util.Objects;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import org.jackhuang.hellominecraft.util.ArrayUtils;
import org.jackhuang.hellominecraft.util.StrUtils;

/**
 * Object which may be returned from
 * <code>WizardPage.WizardResultProducer.finish()</code>
 * or <code>WizardPanelProvider.finish()</code>, or passed to
 * <code>DeferredWizardResult.ResultProgressHandle.finish()</code>. If an
 * instance of <code>Summary</code> is used, then the UI should, rather
 * than disappearing, show the component provided by the <code>Summary</code>
 * object. Convenience constructors are provided for plain text and list style
 * views.
 *
 * @author Tim Boudreau
 */
public class Summary {

    private final Component comp;
    private Object result;

    /**
     * Create a <code>Summary</code> object which will display the
     * passed <code>String</code> in a text component of some sort.
     *
     * @param text   The text to display - must be non-null, greater than zero
     *               length and not completely whitespace
     * @param result The result that should be returned when the Wizard is
     *               closed
     *
     * @return the requested <code>Summary</code> object
     */
    public Summary(String text, Object result) {
        //XXX this is creating components off the AWT thread - needs to change
        //to use invokeAndWait where appropriate
        if (StrUtils.isBlank(text))
            throw new IllegalArgumentException("Text is empty or all whitespace");
        this.result = result;
        JTextArea jta = new JTextArea();
        jta.setText(text);
        jta.setWrapStyleWord(true);
        jta.setLineWrap(true);
        jta.getCaret().setBlinkRate(0);
        jta.setEditable(false);
        jta.getCaret().setVisible(true);
        Font f = UIManager.getFont("Label.font");
        if (f != null) //may be on old GTK L&F, etc.
            jta.setFont(f);
        comp = new JScrollPane(jta);
    }

    /**
     * Create a <code>Summary</code> object that will display the passed
     * <code>String</code>s in a <code>JList</code> or similar.
     *
     * @param items  A non-null list of one or more Strings to be displayed
     * @param result The result that should be returned when the Wizard is
     *               closed
     *
     * @return the requested <code>Summary</code> object
     */
    public Summary(String[] items, Object result) {
        if (ArrayUtils.isEmpty(items))
            throw new IllegalArgumentException("Items array empty");
        this.result = result;
        JList<String> list = new JList<>(items);
        comp = new JScrollPane(list);
    }

    /**
     * Create a <code>Summary</code> object that will display the passed
     * component.
     *
     * @param comp   A custom component to show on the summary page after the
     *               Wizard has been completed
     * @param result The result that should be returned when the
     *               <code>Wizard</code> is
     *               closed
     *
     * @return the requested <code>Summary</code> object
     */
    public Summary(Component comp, Object result) {
        this.result = result;
        this.comp = Objects.requireNonNull(comp, "Null component");
    }

    /**
     * Get the component that will display the summary information.
     *
     * @return an appropriate component, the type of which may differ depending
     *         on the factory method used to create this component
     */
    public Component getSummaryComponent() {
        return comp;
    }

    /**
     * Get the object that represents the actual result of whatever the
     * <code>Wizard</code>
     * that created this <code>Summary</code> object computes. Note this method
     * may not
     * return another instance of <code>Summary</code> or an instance of
     * <code>DeferredWizardResult</code>.
     *
     * @return the object passed to the factory method that created this
     *         Summary object, or null.
     */
    public Object getResult() {
        return result;
    }
}
