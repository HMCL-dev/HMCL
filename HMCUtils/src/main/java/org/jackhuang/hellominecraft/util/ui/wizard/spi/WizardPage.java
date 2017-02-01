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
 * FixedWizard.java
 *
 * Created on August 19, 2005, 9:11 PM
 */
package org.jackhuang.hellominecraft.util.ui.wizard.spi;

import java.beans.Beans;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreePath;
import java.awt.Color;
import java.awt.Component;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jackhuang.hellominecraft.util.ArrayUtils;

/**
 * A convenience JPanel subclass that makes it easy to create wizard panels.
 * This class provides a number of conveniences:
 * <p/>
 * <b>Automatic listening to child components</b><br>
 * If you add an editable component (all standard Swing controls are supported)
 * to a WizardPage or a child JPanel inside it, a listener is automatically
 * attached to it. If user input occurs, the following things happen, in order:
 * <ul>
 * <li>If the <code>name</code> property of the component has been set, then the
 * value from the component (i.e. Boolean for checkboxes, selected item(s) for
 * lists/combo boxes/trees, etc.) will automatically be added to the wizard
 * settings map, with the component name as the key.</li>
 * <li>Regardless of whether the <code>name</code> property is set,
 * <code>validateContents()</code> will be called. You can override that method
 * to enable/disable the finish button, call <code>setProblem()</code> to
 * disable navigation and display a string to the user, etc.
 * </ul>
 * <p/>
 * The above behavior can be disabled by passing <code>false</code> to the
 * appropriate constructor. In that case, <code>validateContents</code> will
 * never be called automatically.
 * <p/>
 * If you have custom components that WizardPage will not know how to listen to
 * automatically, attach an appropriate listener to them and optionally call
 * <code>userInputReceived()</code> with the component and the event if you want
 * to run your automatic validation code.
 * <p/>
 * For convenience, this class implements the relevant methods for accessing the
 * <code>WizardController</code> and the settings map for the wizard that the
 * panel is a part of.
 * <p/>
 * Instances of WizardPage can be returned from a WizardPanelProvider; this
 * class also offers two methods for conveniently assembling a wizard:
 * <ul>
 * <li>Pass an array of already instantiated WizardPages to
 * <code>createWizard()</code>. Note that for large wizards, it is preferable to
 * construct the panels on demand rather than at construction time.</li>
 * <li>Construct a wizard out of WizardPages, instantiating the panels as
 * needed: Pass an array of classes all of which
 * <ul>
 * <li>Are subclasses of WizardPage</li>
 * <li>Have a static method with the following signature:
 * <ul>
 * <li><code>public static String getDescription()</code></li>
 * </ul>
 * </li>
 * </ul>
 * </ul>
 * <p/>
 * Note that during development of a wizard, it is worthwhile to test/run with
 * assertions enabled, as there is quite a bit of validity checking via
 * assertions that can help find problems early.
 * <h2>Using Custom Components</h2>
 * If the <code>autoListen</code> constructor argument is true, a WizardPage
 * will automatically listen to components which have a name, if they are
 * standard Swing components it knows how to listen to. If you are using custom
 * components, implement WizardPage.CustomComponentListener and return it from
 * <code>createCustomComponentListener()</code> to add supplementary listening
 * code for custom components.
 * <p/>
 * Note: Swing components do not fire property changes when setName() is called.
 * If your component's values are not being propagated into the settings map,
 * make sure you are calling setName() <i>before</i> adding the component to the
 * hierarchy.
 * <p/>
 * Also note that cell editors in tables and lists and so forth are always
 * ignored by the automatic listening code.
 *
 * @author Tim Boudreau
 */
public class WizardPage extends JPanel implements WizardPanel {

    private final String description;
    String id;

    //Have an initial dummy map so it's never null.  We'll dump its contents
    //into the real map the first time it's set
    private Map wizardData;
    //An initial wizardController that will dump its settings into the real
    //one the first time it's set
    private WizardControllerImplementation wc = new WC();
    private WizardController controller = new WizardController(wc);

    //Flag to make sure we don't reenter userInputReceieved from maybeUpdateMap()
    private boolean inBeginUIChanged = false;
    //Flag to make sure we don't reenter userInputReceived because the
    //implementation of validateContents changed a component's value, triggering
    //a new event on GenericListener
    private boolean inUiChanged = false;
    private CustomComponentListener ccl;
    private boolean autoListen;

    /**
     * Create a WizardPage with the passed description and auto-listening
     * behavior.
     *
     * @param stepDescription the localized description of this step
     * @param autoListen if true, components added will automatically be
     * listened to for user input
     */
    public WizardPage(String stepDescription, boolean autoListen) {
        this(null, stepDescription, autoListen);
    }

    /**
     * Construct a new WizardPage with the passed step id and description. Use
     * this constructor for WizardPages which will be constructed ahead of time
     * and passed in an array to <code>createWizard</code>.
     *
     * @param stepId the unique ID for the step represented. If null, the class
     * name or a variant of it will be used
     * @param stepDescription the localized description of this step
     * @param autoListen if true, components added will automatically be
     * listened to for user input
     *
     * @see #validateContents
     */
    public WizardPage(String stepId, String stepDescription, boolean autoListen) {
        id = stepId == null ? getClass().getName() : stepId;
        this.autoListen = autoListen;
        description = stepDescription;

    }

    private boolean listening;

    private void startListening() {
        listening = true;
        if (autoListen) {
            //It will attach itself
            GenericListener gl = new GenericListener(this, ccl = createCustomComponentListener(),
                    ccl == null ? null : new CustomComponentNotifierImpl(this));
            gl.attachToHierarchyOf(this);
        } else if ((ccl = createCustomComponentListener()) != null)
            throw new IllegalStateException("CustomComponentListener "
                    + "will never be called if the autoListen parameter is "
                    + "false");
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5)); //XXX
    }

    /**
     * Create an auto-listening WizardPage with the passed description
     *
     * @param stepDescription the localized description of this step
     */
    public WizardPage(String stepDescription) {
        this(null, stepDescription);
    }

    /**
     * Create an auto-listening WizardPage with the passed description
     *
     * @param stepId The unique id for the step. If null, an id will be
     * generated
     * @param stepDescription the localized description of this step
     *
     */
    public WizardPage(String stepId, String stepDescription) {
        this(stepId, stepDescription, true);
    }

    /**
     * Use this constructor or the default constructor if you intend to pass an
     * array of Class<?> objects to lazily create WizardPanels.
     */
    protected WizardPage(boolean autoListen) {
        this(null, null, autoListen);
    }

    /**
     * Default constructor. AutoListening will be on by default.
     */
    protected WizardPage() {
        this(true);
    }

    /**
     * If you are using custom Swing or AWT components which the WizardPage will
     * not know how to automatically listen to, you may want to override this
     * method, implement CustomComponentListener and return an instance of it.
     *
     * @return A CustomComponentListener implementation, or null (the default).
     */
    protected CustomComponentListener createCustomComponentListener() {
        return null;
    }

    /**
     * Implement this class if you are using custom Swing or AWT components, and
     * return an instance of it from
     * <code>WizardPage.createCustomComponentListener()</code>.
     */
    public static abstract class CustomComponentListener {

        /**
         * Indicates that this CustomComponentListener will take responsibility
         * for noticing events from the passed component, and that the
         * WizardPage should not try to automatically listen on it (which it can
         * only do for standard Swing components and their children).
         * <p>
         * Note that this method may be called frequently and any test it does
         * should be fast.
         * <p>
         * <b>Important:</b> The return value from this method should always be
         * the same for any given component, for the lifetime of the WizardPage.
         *
         * @param c A component
         *
         * @return Whether or not this CustomComponentListener will listen on
         * the passed component. If true, the component will later be passed to
         * <code>startListeningTo()</code>
         */
        public abstract boolean accept(Component c);

        /**
         * Begin listening for events on the component. When an event occurs,
         * call the <code>eventOccurred()</code> method on the passed
         * <code>CustomComponentNotifier</code>.
         *
         * @param c The component to start listening to
         * @param n An object that can be called to update the settings map when
         * an interesting event occurs on the component
         */
        public abstract void startListeningTo(Component c, CustomComponentNotifier n);

        /**
         * Stop listening for events on a component.
         *
         * @param c The component to stop listening to
         */
        public abstract void stopListeningTo(Component c);

        /**
         * Determine if the passed component is a container whose children may
         * need to be listened on. Returns false by default.
         *
         * @param c A component which might be a container
         */
        public boolean isContainer(Component c) {
            return false;
        }

        /**
         * Get the map key for this component's value. By default, returns the
         * component's name. Will only be passed components which the
         * <code>accept()</code> method returned true for.
         * <p>
         * <b>Important:</b> The return value from this method should always be
         * the same for any given component, for the lifetime of the WizardPage.
         *
         * @param c the component, which the accept method earlier returned true
         * for
         *
         * @return A string key that should be used in the Wizard's settings map
         * for the name of this component's value
         */
        public String keyFor(Component c) {
            return c.getName();
        }

        /**
         * Get the value currently set on the passed component. Will only be
         * passed components which the <code>accept()</code> method returned
         * true for, and which <code>keyFor()</code> returned non-null.
         *
         * @param c the component
         *
         * @return An object representing the current value of this component.
         * For example, if it were a <code>JTextComponent</code>, the value
         * would likely be the return value of
         * <code>JTextComponent.getText()</code>
         */
        public abstract Object valueFor(Component c);
    }

    /**
     * Object which is passed to
     * <code>CustomComponentListener.startListeningTo()</code>, which can be
     * called when an event has occurred on a custom component the
     * <code>CustomComponentListener</code> has claimed (by returning
     * <code>true</code> from its <code>accept()</code> method).
     */
    public static abstract class CustomComponentNotifier {

        private CustomComponentNotifier() {
        }

        /**
         * Method which may be called when an event occurred on a custom
         * component.
         *
         * @param c the component
         * @param eventObject the event object from the component, or null (with
         * the exception of <code>javax.swing.text.DocumentEvent</code>, it will
         * likely be a subclass of <code>java.util.EventObject</code>).
         */
        public abstract void userInputReceived(Component c, Object eventObject);
    }

    private static final class CustomComponentNotifierImpl extends CustomComponentNotifier {

        private final WizardPage page;

        private CustomComponentNotifierImpl(WizardPage page) {
            this.page = page; //Slightly smaller footprint a nested, not inner class
        }

        @Override
        public void userInputReceived(Component c, Object event) {
            if (!page.ccl.accept(c))
                return;
            page.userInputReceived(c, event);
        }
    }

    String id() {
        return getID();
    }

    String description() {
        return getDescription();
    }

    private String getID() {
        return id;
    }

    private String getDescription() {
        return description;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!listening)
            startListening();

        renderingPage();
        inValidateContents = true;
        try {
            setProblem(validateContents(null, null));
        } finally {
            inValidateContents = false;
        }
    }

    @Override
    public WizardPanelNavResult allowBack(String stepName, Map settings, Wizard wizard) {
        return WizardPanelNavResult.PROCEED;
    }

    @Override
    public WizardPanelNavResult allowFinish(String stepName, Map settings, Wizard wizard) {
        return WizardPanelNavResult.PROCEED;
    }

    @Override
    public WizardPanelNavResult allowNext(String stepName, Map settings, Wizard wizard) {
        return WizardPanelNavResult.PROCEED;
    }

    private boolean inValidateContents = false;

    /**
     * Called whenever the page is rendered. This can be used by the page as a
     * notification to load page-specific information in its fields.
     * <p/>
     * By default, this method does nothing.
     */
    protected void renderingPage() {
        // Empty
    }

    /**
     * Create a simple Wizard from an array of <code>WizardPage</code>s
     */
    public static Wizard createWizard(WizardPage[] contents, WizardResultProducer finisher) {
        return new WPP(contents, finisher).createWizard();
    }

    public static Wizard createWizard(String title, WizardPage[] contents, WizardResultProducer finisher) {
        return new WPP(title, contents, finisher).createWizard();
    }

    public static Wizard createWizard(String title, WizardPage[] contents) {
        return createWizard(title, contents, WizardResultProducer.NO_OP);
    }

    /**
     * Create a simple Wizard from an array of WizardPages, with a no-op
     * WizardResultProducer.
     */
    public static Wizard createWizard(WizardPage[] contents) {
        return createWizard(contents, WizardResultProducer.NO_OP);
    }

    /**
     * Create simple Wizard from an array of classes, each of which is a unique
     * subclass of WizardPage.
     */
    public static Wizard createWizard(Class<?>[] wizardPageClasses, WizardResultProducer finisher) {
        return new CWPP(wizardPageClasses, finisher).createWizard();
    }

    /**
     * Create simple Wizard from an array of classes, each of which is a unique
     * subclass of WizardPage.
     */
    public static Wizard createWizard(String title, Class<?>[] wizardPageClasses, WizardResultProducer finisher) {
        return new CWPP(title, wizardPageClasses, finisher).createWizard();
    }

    /**
     * Create simple Wizard from an array of classes, each of which is a unique
     * subclass of WizardPage.
     */
    public static Wizard createWizard(String title, Class<?>[] wizardPageClasses) {
        return new CWPP(title, wizardPageClasses,
                WizardResultProducer.NO_OP).createWizard();
    }

    /**
     * Create a simple Wizard from an array of classes, each of which is a
     * unique subclass of WizardPage, with a no-op WizardResultProducer.
     */
    public static Wizard createWizard(Class<?>[] wizardPageClasses) {
        return createWizard(wizardPageClasses, WizardResultProducer.NO_OP);
    }

    /**
     * Called by createPanelForStep, with whatever map is passed. In the current
     * impl this is always the same Map, but that is not guaranteed. If any
     * content was added by calls to putWizardData() during the constructor,
     * etc., such data is copied to the settings map the first time this method
     * is called.
     *
     * Subclasses do NOT need to override this method, they can override
     * renderPage which is always called AFTER the map has been made valid.
     */
    void setWizardDataMap(Map m) {
        if (m == null)
            wizardData = new HashMap<>();
        else {
            if (wizardData instanceof HashMap)
                // our initial map has keys for all of our components
                // but with dummy empty values
                // So make sure we don't override data that was put in as part of the initialProperties
                for (Map.Entry entry : (Set<Map.Entry>) wizardData.entrySet()) {
                    Object key = entry.getKey();
                    if (!m.containsKey(key))
                        m.put(key, entry.getValue());
                }
            wizardData = m;
        }
    }

    /**
     * Set the WizardController. In the current impl, this is always the same
     * object, but the API does not guarantee that. The first time this is
     * called, it will update the state of the passed controller to match any
     * state that was set by components during the construction of this
     * component
     */
    void setController(WizardController controller) {
        if (controller.getImpl() instanceof WC)
            ((WC) controller.getImpl()).configure(controller);

        this.controller = controller;
    }

    /**
     * Get the WizardController for interacting with the Wizard that contains
     * this panel. Return value will never be null.
     */
    private WizardController getController() {
        return controller;
    }

    /**
     * Set the problem string. Call this method if next/finish should be
     * disabled. The passed string will be visible to the user, and should be a
     * short, localized description of what is wrong.
     */
    protected final void setProblem(String value) {
        getController().setProblem(value);
    }

    /**
     * Set whether the finish, next or both buttons should be enabled, assuming
     * no problem string is set.
     *
     * @param value WizardController.MODE_CAN_CONTINUE,
     * WizardController.MODE_CAN_FINISH or
     * WizardController.MODE_CAN_CONTINUE_OR_FINISH;
     */
    protected final void setForwardNavigationMode(int value) {
        getController().setForwardNavigationMode(value);
    }

    /**
     * Disable all navigation. Useful if some background task is being completed
     * during which no navigation should be allowed. Use with care, as it
     * disables the cancel button as well.
     */
    protected final void setBusy(boolean busy) {
        getController().setBusy(busy);
    }

    /**
     * Store a value in response to user interaction with a GUI component.
     */
    protected final void putWizardData(Object key, Object value) {
        getWizardDataMap().put(key, value);
        if (!inBeginUIChanged && !inValidateContents) {
            inValidateContents = true;
            try {
                setProblem(validateContents(null, null));
            } finally {
                inValidateContents = false;
            }
        }
    }

    /**
     * Returns all of the keys in the wizard data map.
     */
    protected final Object[] getWizardDataKeys() {
        return getWizardDataMap().keySet().toArray();
    }

    /**
     * Retrieve a value stored in the wizard map, which may have been
     * putWizardData there by this panel or any previous panel in the wizard
     * which contains this panel.
     */
    protected final Object getWizardData(Object key) {
        return getWizardDataMap().get(key);
    }

    /**
     * Determine if the wizard map contains the requested key.
     */
    protected final boolean wizardDataContainsKey(Object key) {
        return getWizardDataMap().containsKey(key);
    }

    /**
     * Called when an event is received from one of the components in the panel
     * that indicates user input. Typically you won't need to touch this method,
     * unless your panel contains custom components which are not subclasses of
     * any standard Swing component, which the framework won't know how to
     * listen for changes on. For such cases, attach a listener to the custom
     * component, and call this method with the event if you want validation to
     * run when input happens. Automatic updating of the settings map will not
     * work for such custom components, for obvious reasons, so update the
     * settings map, if needed, in validateContents for this case.
     *
     * @param source The component that the user interacted with (if it can be
     * determined from the event) or null
     * @param event Usually an instance of EventObject, except in the case of
     * DocumentEvent.
     */
    protected final void userInputReceived(Component source, Object event) {
        if (inBeginUIChanged)
            return;

        //Update the map no matter what
        inBeginUIChanged = true;

        if (source != null)
            try {
                maybeUpdateMap(source);
            } finally {
                inBeginUIChanged = false;
            }

        //Possibly some programmatic change from checkState could cause
        //a recursive call
        if (inUiChanged)
            return;

        inUiChanged = true;
        inValidateContents = true;
        try {
            setProblem(validateContents(source, event));
        } finally {
            inUiChanged = false;
            inValidateContents = false;
        }
    }

    /**
     * Puts the value from the component in the settings map if the component's
     * name property is not null
     */
    void maybeUpdateMap(Component comp) {
        Object mapKey = getMapKeyFor(comp);
        // debug: System.err.println("MaybeUpdateMap " + mapKey + " from " + comp);
        if (mapKey != null) {
            Object value = valueFrom(comp);
            putWizardData(mapKey, value);
        }
    }

    /**
     * Callback for GenericListener to remove a component's value if its name
     * changes or it is removed from the panel.
     */
    void removeFromMap(Object key) {
        getWizardDataMap().remove(key);
    }

    /**
     * Given an ad-hoc swing component, fetch the likely value based on its
     * state. The default implementation handles most common swing components.
     * If you are using custom components and have assigned them names, override
     * this method to handle getting an appropriate value out of your custom
     * component and call super for the others.
     */
    protected Object valueFrom(Component comp) {
        if (ccl != null && ccl.accept(comp))
            return ccl.valueFor(comp);
        if (comp instanceof JRadioButton || comp instanceof JCheckBox || comp instanceof JToggleButton)
            return ((AbstractButton) comp).getModel().isSelected() ? Boolean.TRUE : Boolean.FALSE;
        else if (comp instanceof JTree) {
            TreePath path = ((JTree) comp).getSelectionPath();
            if (path != null)
                return path.getLastPathComponent();
        } else if (comp instanceof JFormattedTextField)
            return ((JFormattedTextField) comp).getValue();
        else if (comp instanceof JList) {
            Object[] o = ((JList<?>) comp).getSelectedValues();
            if (o.length > 1)
                return o;
            else if (o.length == 1)
                return o[0];
        } else if (comp instanceof JTextComponent)
            return ((JTextComponent) comp).getText();
        else if (comp instanceof JComboBox)
            return ((JComboBox<?>) comp).getSelectedItem();
        else if (comp instanceof JColorChooser)
            return ((JColorChooser) comp).getSelectionModel().getSelectedColor();
        else if (comp instanceof JSpinner)
            return ((JSpinner) comp).getValue();
        else if (comp instanceof JSlider)
            return ((JSlider) comp).getValue();

        return null;
    }

    /**
     * Given an ad-hoc swing component, set the value as the property from the
     * settings. The default implementation handles most common swing
     * components. If you are using custom components and have assigned them
     * names, override this method to handle getting an appropriate value out of
     * your custom component and call super for the others.
     */
    protected void valueTo(Map settings, Component comp) {
        String name = comp.getName();
        Object value = settings.get(name);
        if (comp instanceof JRadioButton || comp instanceof JCheckBox || comp instanceof JToggleButton) {
            if (value instanceof Boolean)
                ((AbstractButton) comp).getModel().setSelected(((Boolean) value));
// TOFIX: JTree
        } else if (comp instanceof JFormattedTextField)
            ((JFormattedTextField) comp).setValue(value); //      }  else if (comp instanceof JTree) {
        //            TreePath path = ((JTree) comp).getSelectionPath();
        //            if (path != null) {
        //                return path.getLastPathComponent();
        //            }
        else if (comp instanceof JList) {
            if (value instanceof Object[])
                throw new IllegalArgumentException("can't handle multi-select lists");
            ((JList<?>) comp).setSelectedValue(value, true);
        } else if (comp instanceof JTextComponent)
            ((JTextComponent) comp).setText((String) value);
        else if (comp instanceof JComboBox)
            ((JComboBox) comp).setSelectedItem(value);
        else if (comp instanceof JColorChooser)
            ((JColorChooser) comp).getSelectionModel().setSelectedColor((Color) value);
        else if (comp instanceof JSpinner)
            ((JSpinner) comp).setValue(value);
        else if (comp instanceof JSlider)
            ((JSlider) comp).setValue(((Integer) value));
    }

    /**
     * Get the map key that should be used to automatically put the value
     * represented by this component into the wizard data map.
     * <p/>
     * The default implementation returns the result of
     * <code>c.getName()</code>, which is almost always sufficient and
     * convenient - just set the component names in a GUI builder and everything
     * will be handled.
     *
     * @return null if the component's value should not be automatically written
     * to the wizard data map, or an object which is the key that later code
     * will use to find this value. By default, it returns the component's name.
     */
    protected Object getMapKeyFor(Component c) {
        if (ccl != null && ccl.accept(c))
            return ccl.keyFor(c);
        else
            return c.getName();
    }

    /**
     * Called when user interaction has occurred on a component contained by
     * this panel or one of its children. Override this method to check if all
     * of the values are legal, such that the Next/Finish button should be
     * enabled, optionally calling <code>setForwardNavigationMode()</code> if
     * warranted.
     * <p/>
     * This method also may be called with a null argument an effect of calling
     * <code>putWizardData()</code> from someplace other than within this
     * method.
     * <p/>
     * Note that this method may be called very frequently, so it is important
     * that validation code be fast. For cases such as
     * <code>DocumentEvent</code>s, it may be desirable to delay validation with
     * a timer, if the implementation of this method is too expensive to call on
     * each keystroke.
     * <p/>
     * Either the component, or the event, or both may be null on some calls to
     * this method (such as when it is called because the settings map has been
     * written to).
     * <p/>
     * The default implementation returns null.
     *
     * @param component The component the user interacted with, if it can be
     * determined. The infrastructure does track the owners of list models and
     * such, and can find the associated component, so this will usually (but
     * not necessarily) be non-null.
     * @param event The event object (if any) that triggered this call to
     * validateContents. For most cases this will be an instance of EventObject,
     * and can be used to directly detect what component the user interacted
     * with. Since javax.swing.text.DocumentEvent is not a subclass of
     * EventObject, the type of the argument is Object, so these events may be
     * passed.
     *
     * @return A localized string describing why navigation should be disabled,
     * or null if the state of the components is valid and forward navigation
     * should be enabled.
     */
    protected String validateContents(Component component, Object event) {
        return null;
    }

    /**
     * Called if the user is navigating into this panel when it has already been
     * displayed at least once - the user has navigated back to this panel, or
     * back past this panel and is now navigating forward again.
     * <p/>
     * If some of the UI needs to be set up based on values from earlier pages
     * that may have changed, do that here, fetching values from the settings
     * map by calling <code>getWizardData()</code>.
     * <p/>
     * The default implementation simply calls
     * <code>validateContents (null, null)</code>.
     */
    protected void recycle() {
        setProblem(validateContents(null, null));
    }

    /**
     * Get the settings map into which the wizard gathers settings. Return value
     * will never be null.
     */
    // the map is empty during construction, then later set to the map from the containing WizardController
    protected Map getWizardDataMap() {
        if (wizardData == null)
            wizardData = new HashMap();
        return wizardData;
    }

    private String longDescription;

    /**
     * Set the long description of this page. This method may be called only
     * once and should be called from within the constructor.
     *
     * @param desc The long description for this step
     */
    protected void setLongDescription(String desc) {
        if (!Beans.isDesignTime() && this.longDescription != null)
            throw new IllegalStateException("Long description already set to"
                    + " " + desc);
        this.longDescription = desc;
    }

    /**
     * Get the long description of this page, which should be used in the title
     * area of the wizard's UI if non-null. To use, call setLongDescription() in
     * your WizardPage's constructor. It may be set only once.
     *
     * @return the description
     */
    public final String getLongDescription() {
        return longDescription;
    }

    static WizardPanelProvider createWizardPanelProvider(WizardPage page) {
        return new WPP(new WizardPage[] { page }, WizardResultProducer.NO_OP);
    }

    static WizardPanelProvider createWizardPanelProvider(WizardPage[] page) {
        return new WPP(page, WizardResultProducer.NO_OP);
    }

    /**
     * WizardPanelProvider that takes an array of already created WizardPages
     */
    static final class WPP extends WizardPanelProvider {

        private final WizardPage[] pages;
        private final WizardResultProducer finish;

        WPP(WizardPage[] pages, WizardResultProducer finish) {
            super(Util.getSteps(pages), Util.getDescriptions(pages));

            //Fail-fast validation - don't wait until something goes wrong
            //if the data are bad
            // assert valid(pages) == null : valid(pages);
            // assert finish != null;
            String v = valid(pages);
            if (v != null)
                throw new RuntimeException(v);
            if (finish == null)
                throw new RuntimeException("finish must not be null");

            this.pages = pages;
            this.finish = finish;
        }

        WPP(String title, WizardPage[] pages, WizardResultProducer finish) {
            super(title, Util.getSteps(pages), Util.getDescriptions(pages));

            //Fail-fast validation - don't wait until something goes wrong
            //if the data are bad
            // assert valid(pages) == null : valid(pages);
            // assert finish != null;
            String v = valid(pages);
            if (v != null)
                throw new RuntimeException(v);
            if (finish == null)
                throw new RuntimeException("finish must not be null");

            this.pages = pages;
            this.finish = finish;
        }

        @Override
        protected JComponent createPanel(WizardController controller, String id,
                Map wizardData) {
            int idx = indexOfStep(id);

            // assert idx != -1 : "Bad ID passed to createPanel: " + id;
            if (idx == -1)
                throw new RuntimeException("Bad ID passed to createPanel: " + id);
            pages[idx].setController(controller);
            pages[idx].setWizardDataMap(wizardData);

            return pages[idx];
        }

        /**
         * Make sure we haven't been passed bogus data
         */
        private String valid(WizardPage[] pages) {
            if (ArrayUtils.hasDuplicateElements(pages))
                return "Duplicate entry in array: "
                        + Arrays.asList(pages);

            for (int i = 0; i < pages.length; i++)
                if (pages[i] == null)
                    return "Null entry at " + i + " in pages array";

            return null;
        }

        @Override
        protected Object finish(Map settings) throws WizardException {
            return finish.finish(settings);
        }

        @Override
        public boolean cancel(Map settings) {
            return finish.cancel(settings);
        }

        @Override
        public String getLongDescription(String stepId) {
            for (WizardPage wizardPage : pages)
                if (stepId.equals(wizardPage.getID()))
                    return wizardPage.getLongDescription();
            return null;
        }
    }

    /**
     * WizardPanelProvider that takes an array of WizardPage subclasses and
     * instantiates them on demand
     */
    private static final class CWPP extends WizardPanelProvider {

        private final Class<?>[] classes;
        private final WizardResultProducer finish;
        private final String[] longDescriptions;

        CWPP(String title, Class<?>[] classes, WizardResultProducer finish) {
            super(title, Util.getSteps(classes), Util.getDescriptions(classes));
            _validateArgs(classes, finish);
            this.finish = finish;
            this.classes = classes;
            longDescriptions = new String[classes.length];
        }

        private void _validateArgs(Class<?>[] classes, WizardResultProducer finish) {
            Objects.requireNonNull(classes, "Class<?> array may not be null");
            Objects.requireNonNull(finish, "WizardResultProducer may not be null");
            if (ArrayUtils.hasDuplicateElements(classes))
                throw new RuntimeException("Duplicate entries in class array");
        }

        CWPP(Class<?>[] classes, WizardResultProducer finish) {
            super(Util.getSteps(classes), Util.getDescriptions(classes));
            longDescriptions = new String[classes.length];
            _validateArgs(classes, finish);

            this.classes = classes;
            this.finish = finish;
        }

        @Override
        protected JComponent createPanel(WizardController controller, String id, Map wizardData) {
            int idx = indexOfStep(id);

            // assert idx != -1 : "Bad ID passed to createPanel: " + id;
            if (idx == -1)
                throw new RuntimeException("Bad ID passed to createPanel: " + id);
            try {
                WizardPage result = (WizardPage) classes[idx].newInstance();
                longDescriptions[idx] = result.getLongDescription();

                result.setController(controller);
                result.setWizardDataMap(wizardData);

                return result;
            } catch (IllegalAccessException | InstantiationException e) {
                // really IllegalArgumentException, but we need to have the "cause" get shown in stack trace
                throw new RuntimeException("Could not instantiate "
                        + classes[idx], e);
            }
        }

        @Override
        protected Object finish(Map settings) throws WizardException {
            return finish.finish(settings);
        }

        @Override
        public boolean cancel(Map settings) {
            return finish.cancel(settings);
        }

        @Override
        public String toString() {
            return super.toString() + " for " + finish;
        }

        @Override
        public String getLongDescription(String stepId) {
            int idx = indexOfStep(stepId);
            if (idx != -1)
                return longDescriptions[idx] == null ? descriptions[idx]
                        : longDescriptions[idx];
            return null;
        }
    }

    /**
     * A dummy wizard controller which is used until the panel has actually been
     * put into use; so state can be set during the constructor, etc. Its state
     * will be dumped into the real one once there is a real one.
     */
    private static final class WC implements WizardControllerImplementation {

        private String problem = null;
        private int canFinish = -1;
        private Boolean busy = null;

        @Override
        public void setProblem(String value) {
            this.problem = value;
        }

        @Override
        public void setForwardNavigationMode(int value) {
            switch (value) {
                case WizardController.MODE_CAN_CONTINUE:
                case WizardController.MODE_CAN_FINISH:
                case WizardController.MODE_CAN_CONTINUE_OR_FINISH:
                    break;
                default:
                    throw new IllegalArgumentException(Integer.toString(value));
            }

            canFinish = value;
        }

        @Override
        public void setBusy(boolean busy) {
            this.busy = busy ? Boolean.TRUE : Boolean.FALSE;
        }

        void configure(WizardController other) {
            if (other == null)
                return;

            if (busy != null)
                other.setBusy(busy);

            if (canFinish != -1)
                other.setForwardNavigationMode(canFinish);

            if (problem != null)
                other.setProblem(problem);
        }
    }

    /**
     * Interface that is passed to WizardPage.createWizard(). For wizards
     * created from a set of WizardPages or WizardPage subclasses, this is the
     * object that whose code will be run to create or do whatever the wizard
     * does when the user clicks the Finish button.
     */
    public static interface WizardResultProducer {

        /**
         * Conclude a wizard, doing whatever the wizard does with the data
         * gathered into the map on the various panels.
         * <p>
         * If an instance of <code>Summary</code> is returned from this method,
         * the UI shall display it on a final page and disable all navigation
         * buttons except the Close/Cancel button.
         * <p>
         * If an instance of <code>DeferredWizardResult</code> is returned from
         * this method, the UI shall display some sort of progress bar while the
         * result is computed in the background. If that
         * <code>DeferredWizardResult</code> produces a <code>Summary</code>
         * object, that summary shall be displayed as described above.
         *
         * @param wizardData the map with key-value pairs which has been
         * populated by the UI as the user progressed through the wizard
         *
         * @return an object composed based on what the user entered in the
         * wizard - somethingmeaningful to whatever code invoked the wizard, or
         * null. Note special handling if an instance of
         * <code>DeferredWizardResult</code> or <code>Summary</code> is returned
         * from this method.
         */
        Object finish(Map wizardData) throws WizardException;

        /**
         * Called when the user presses the cancel button. Almost all
         * implementations will want to return true.
         */
        boolean cancel(Map settings);

        /**
         * A no-op WizardResultProducer that returns null.
         */
        WizardResultProducer NO_OP = new WizardResultProducer() {
            @Override
            public Object finish(Map wizardData) {
                return wizardData;
            }

            @Override
            public boolean cancel(Map settings) {
                return true;
            }

            @Override
            public String toString() {
                return "NO_OP WizardResultProducer";
            }
        };
    }

}
