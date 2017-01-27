/*
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the License). You may not use this file except in
 * compliance with the License.
 * You can obtain a copy of the License at http://www.netbeans.org/cddl.html
 * or http://www.netbeans.org/cddl.txt.
 * When distributing Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://www.netbeans.org/cddl.txt.
 * If applicable, add the following below the CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
package org.jackhuang.hellominecraft.util.ui.wizard.api;

import java.awt.Container;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Map;
import javax.swing.Action;
import org.jackhuang.hellominecraft.util.ArrayUtils;
import org.jackhuang.hellominecraft.util.ui.wizard.api.displayer.WizardDisplayerImpl;
import org.jackhuang.hellominecraft.util.ui.wizard.spi.Wizard;

/**
 * <h2>Displaying Wizards</h2>
 * Factory which can display a <code>Wizard</code> in a dialog onscreen or in an
 * ad-hoc
 * container. Usage:
 * <pre>
 * Wizard wizard = WizardPage.createWizard (new Class[] {WizardPageSubclass1.class,
 *     WizardPageSubclass2.class, WizardPageSubclass3.class},
 *     new MyWizardResultProducer();
 * WizardDisplayer.showWizard (wizard);
 * </pre>
 * Alternately you can implement <code>WizardPanelProvider</code> instead of
 * <code>WizardPage</code> to provide the panels of the wizard.
 * <p>
 * To use a <code>Wizard</code> in a <code>JInternalFrame</code> or similar, use
 * <code>WizardDisplayer.installInContainer()</code>. You will need to implement
 * <code>WizardResultReceiver</code> which will me notified when the wizard
 * is finished or cancelled, to close the internal frame or whatever UI is
 * showing the wizard.
 * <p>
 * <h2>Customizing the default implementation</h2>
 * The image on the left panel of the default implementation can be customized
 * in the following ways:
 * <ul>
 * <li>Put an instance of <code>java.awt.image.BufferedImage</code> into
 * UIManager with the key <code>wizard.sidebar.image</code>, i.e.
 * <pre>
 *    BufferedImage img = ImageIO.read (getClass().getResource ("MySideImage.png");
 *    UIManager.put ("wizard.sidebar.image", img);
 * </pre>
 * </li>
 * <li>Use the system property <code>wizard.sidebar.image</code> to set a path
 * within a JAR on the classpath to the image. The image must be visible
 * to the classloader which loads <code>WizardDisplayer</code>, so this
 * may not work in environments which manage the classpath. i.e.
 * <pre>
 *    System.setProperty ("wizard.sidebar.image", "com/foo/myapp/MySideImage.png");
 * </pre>
 * </li>
 * </ul>
 *
 * <h2>Providing a custom WizardDisplayer:</h2>
 * The implementation of <code>WizardDisplayer</code> is pluggable. While the
 * default implementation should be adequate for most cases, it is possible
 * that in some cases one might want to completely replace the UI, buttons,
 * etc. with custom UI code. To do that:
 * <ul>
 * <li>If the NetBeans Lookup library (<code>org.openide.util.Lookup</code>
 * is on the classpath, the default implementation will be found in
 * the default lookup (i.e. META-INF/services, same as
 * JDK 6's ServiceLoader)</li>
 * <li>If Lookup is not available or not found, <code>WizardDisplayer</code>
 * will check the system
 * property <code>WizardDisplayer.default</code> for a fully qualified
 * class name of a subclass of <code>WizardDisplayer</code>.
 * </li>
 * <li>If no other implementation of <code>WizardDisplayer</code> is found
 * by the above methods, the default implementation contained in this
 * library will be used.</li>
 * </ul>
 *
 * @author Tim Boudreau
 */
public abstract class WizardDisplayer {

    protected WizardDisplayer() {
    }

    /**
     * Display a wizard in a dialog, using the default implementation of
     * WizardDisplayer.
     *
     * @param wizard            The wizard to show. Must not be null
     * @param rect              The rectangle on screen for the wizard, may be
     *                          null for default size
     * @param help              An action to invoke if the user presses the help
     *                          button
     * @param initialProperties are the initial values for properties to be
     *                          shown
     *                          and entered in the wizard. May be null.
     */
    public static Object showWizard(Wizard wizard, Rectangle rect, Action help, Map<?, ?> initialProperties) {
        // assert nonBuggyWizard (wizard);
        // validate it
        nonBuggyWizard(wizard);

        WizardDisplayer defaultInstance = getDefault();

        return defaultInstance.show(wizard, rect, help, initialProperties);
    }

    private static WizardDisplayer getDefault() {
        return new WizardDisplayerImpl();
    }

    /**
     * Show a wizard with default window placement and no Help button
     */
    public static Object showWizard(Wizard wizard) {
        return showWizard(wizard, null, null, null);
    }

    /**
     * Show a wizard.
     *
     * @param wizard            the Wizard to show
     * @param r                 the bounding rectangle for the wizard dialog on
     *                          screen, null means "computed from first panel
     *                          size"
     * @param help              An action to be called if the Help button is
     *                          pressed
     * @param initialProperties are used to set initial values for screens
     *                          within the wizard.
     *                          This may be null.
     *
     * @return Whatever object the wizard returns from its <code>finish()</code>
     *         method, if the Wizard was completed by the user.
     */
    protected abstract Object show(Wizard wizard, Rectangle r, Action help, Map<?, ?> initialProperties);

    /**
     * Instance implementation of installInContainer().
     */
    protected abstract void install(Container c, Object layoutConstraint,
                                    Wizard awizard, Action helpAction, Map<?, ?> initialProperties,
                                    WizardResultReceiver receiver);

    private static boolean nonBuggyWizard(Wizard wizard) {
        String[] s = wizard.getAllSteps();
        // for JDK 1.4.2: replace assert with runtime exception
        if (ArrayUtils.hasDuplicateElements(s))
            throw new RuntimeException("steps are duplicated: " + Arrays.asList(s));
        if (s.length == 1 && Wizard.UNDETERMINED_STEP.equals(s[0]))
            // assert false : "Only ID may not be UNDETERMINED_ID";
            throw new RuntimeException("Only ID may not be UNDETERMINED_ID");
        for (int i = 0; i < s.length; i++)
            if (Wizard.UNDETERMINED_STEP.equals(s[i]) && i != s.length - 1)
                //  assert false :  "UNDETERMINED_ID may only be last element in" +
                //        " ids array " + Arrays.asList(s);
                throw new RuntimeException("UNDETERMINED_ID may only be last element in ids array " + Arrays.asList(s));
        return true;
    }

}
