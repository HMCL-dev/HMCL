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
 * GenericListener.java
 *
 * Created on October 5, 2004, 12:36 AM
 */
package org.jackhuang.hellominecraft.util.ui.wizard.spi;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.EventObject;
import java.util.HashSet;
import java.util.Set;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreeSelectionModel;

/**
 * A listener that can listen to just about any standard swing component
 * that accepts user input and notify the panel that it needs to
 * validate its contents.
 *
 * If you use subclasses of the swing components, you will also need to subclass
 * this listener and override at least the methods isProbablyContainer, attachTo
 * and detachFrom.
 *
 * @author Tim Boudreau
 */
final class GenericListener
    implements ActionListener, PropertyChangeListener, ItemListener,
               ContainerListener, DocumentListener, ChangeListener,
               ListSelectionListener, TreeSelectionListener, TableModelListener {

    private final WizardPage wizardPage;

    private boolean ignoreEvents;

    /**
     * Set of components that we're listening to models of, so we can look
     * up the component from the model as needed
     */
    private final Set<Component> listenedTo = new HashSet<>();

    private final WizardPage.CustomComponentListener extListener;
    private final WizardPage.CustomComponentNotifier extNotifier;

    public GenericListener(WizardPage wizardPage, WizardPage.CustomComponentListener l,
                           WizardPage.CustomComponentNotifier n) {
        this.extListener = l;
        this.extNotifier = n;
        if ((extListener == null) != (extNotifier == null))
            throw new RuntimeException();
        // assert wizardPage != null : "WizardPage may not be null";
        if (wizardPage == null)
            throw new IllegalArgumentException("WizardPage may not be null");
        this.wizardPage = wizardPage;
        wizardPage.addContainerListener(this);
    }

    public GenericListener(WizardPage page) {
        this(page, null, null);
    }

    /**
     * Return true if the given component is likely to be a container such the
     * each
     * component within the container should be be considered as a user input.
     *
     * @param c
     *
     * @return true if the component children should have this listener added.
     */
    protected boolean isProbablyAContainer(Component c) {
        boolean result = extListener != null ? extListener.isContainer(c) : false;
        if (!result) {
            boolean isSwing = isSwingClass(c);
            if (isSwing)
                result = c instanceof JPanel || c instanceof JSplitPane || c instanceof JToolBar || c instanceof JViewport || c instanceof JScrollPane
                         || c instanceof JFrame || c instanceof JRootPane || c instanceof Window || c instanceof Frame || c instanceof Dialog
                         || c instanceof JTabbedPane || c instanceof JInternalFrame
                         || c instanceof JDesktopPane || c instanceof JLayeredPane
                         || c instanceof Box;
            else
                result = c instanceof Container;
        }
        return result;
    }

    /**
     * Return true if the given component is likely to be a swing primitive or a
     * subclass.
     * The default implmentation here just checks for the package of the
     * component to be "javax.swing"
     * If you use subclasses of swing components, you will need to override this
     * method
     * to get proper behavior.
     *
     * @param c
     *
     * @return true if the component should be examined more closely (see
     *         isProbablyAContainer)
     */
    protected boolean isSwingClass(Component c) {
        String packageName = c.getClass().getPackage().getName();
        boolean swing = packageName.equals("javax.swing");
        return swing;
    }

    protected void attachTo(Component jc) {
        if (extListener != null && extListener.accept(jc)) {
            extListener.startListeningTo(jc, extNotifier);
            listenedTo.add(jc);
            if (wizardPage.getMapKeyFor(jc) != null)
                wizardPage.maybeUpdateMap(jc);
            return;
        }
        if (isProbablyAContainer(jc))
            attachToHierarchyOf((Container) jc);
        else if (jc instanceof JList) {
            listenedTo.add(jc);
            ((JList<?>) jc).addListSelectionListener(this);
        } else if (jc instanceof JComboBox)
            ((JComboBox<?>) jc).addActionListener(this);
        else if (jc instanceof JTree) {
            listenedTo.add(jc);
            ((JTree) jc).getSelectionModel().addTreeSelectionListener(this);
        } else if (jc instanceof JToggleButton)
            ((AbstractButton) jc).addItemListener(this);
        else if (jc instanceof JFormattedTextField)        //JFormattedTextField must be tested before JTextCompoent
            jc.addPropertyChangeListener("value", this);
        else if (jc instanceof JTextComponent) {
            listenedTo.add(jc);
            ((JTextComponent) jc).getDocument().addDocumentListener(this);
        } else if (jc instanceof JColorChooser) {
            listenedTo.add(jc);
            ((JColorChooser) jc).getSelectionModel().addChangeListener(this);
        } else if (jc instanceof JSpinner)
            ((JSpinner) jc).addChangeListener(this);
        else if (jc instanceof JSlider)
            ((JSlider) jc).addChangeListener(this);
        else if (jc instanceof JTable) {
            listenedTo.add(jc);
            ((JTable) jc).getSelectionModel().addListSelectionListener(this);
        }

        if (accept(jc) && !(jc instanceof JPanel)) {
            jc.addPropertyChangeListener("name", this);
            if (wizardPage.getMapKeyFor(jc) != null)
                wizardPage.maybeUpdateMap(jc);
        }
    }

    protected void detachFrom(Component jc) {
        listenedTo.remove(jc);
        if (extListener != null && extListener.accept(jc))
            extListener.stopListeningTo(jc);
        if (isProbablyAContainer(jc))
            detachFromHierarchyOf((Container) jc);
        else if (jc instanceof JList)
            ((JList<?>) jc).removeListSelectionListener(this);
        else if (jc instanceof JComboBox)
            ((JComboBox<?>) jc).removeActionListener(this);
        else if (jc instanceof JTree)
            ((JTree) jc).getSelectionModel().removeTreeSelectionListener(this);
        else if (jc instanceof JToggleButton)
            ((AbstractButton) jc).removeActionListener(this);
        else if (jc instanceof JTextComponent) {
        } else if (jc instanceof JFormattedTextField) {        //JFormattedTextField must be tested before JTextCompoent
            jc.removePropertyChangeListener("value", this);
            ((JTextComponent) jc).getDocument().removeDocumentListener(this);
        } else if (jc instanceof JColorChooser)
            ((JColorChooser) jc).getSelectionModel().removeChangeListener(this);
        else if (jc instanceof JSpinner)
            ((JSpinner) jc).removeChangeListener(this);
        else if (jc instanceof JSlider)
            ((JSlider) jc).removeChangeListener(this);
        else if (jc instanceof JTable)
            ((JTable) jc).getSelectionModel().removeListSelectionListener(this);

        if (accept(jc) && !(jc instanceof JPanel)) {
            jc.removePropertyChangeListener("name", this);
            Object key = wizardPage.getMapKeyFor(jc);

            if (key != null)
                wizardPage.removeFromMap(key);
        }
    }

    private void detachFromHierarchyOf(Container container) {
        container.removeContainerListener(this);
        Component[] components = container.getComponents();
        for (Component component : components)
            detachFrom(component); // Will callback recursively any nested JPanels
    }

    void attachToHierarchyOf(Container container) {
        if (!Arrays.asList(container.getContainerListeners()).contains(this))
            container.addContainerListener(this);
        Component[] components = container.getComponents();
        for (Component component : components)
            attachTo(component); // Will recursively add any child components in
    }

    protected boolean accept(Component jc) {
        if (extListener != null && extListener.accept(jc))
            return true;
        if (!(jc instanceof JComponent))
            return false;
        if (jc instanceof TableCellEditor || jc instanceof TreeCellEditor
            || SwingUtilities.getAncestorOfClass(JTable.class, jc) != null
            || SwingUtilities.getAncestorOfClass(JTree.class, jc) != null
            || SwingUtilities.getAncestorOfClass(JList.class, jc) != null)
            //Don't listen to cell editors, we can end up listening to them
            //multiple times, and the tree/table model will give us the event
            //we need
            return false;
        return isProbablyAContainer(jc)
               || jc instanceof JList
               || jc instanceof JComboBox
               || jc instanceof JTree
               || jc instanceof JToggleButton
               || //covers toggle, radio, checkbox
            jc instanceof JTextComponent
               || jc instanceof JColorChooser
               || jc instanceof JSpinner
               || jc instanceof JSlider;
    }

    void setIgnoreEvents(boolean val) {
        ignoreEvents = val;
    }

    private void fire(Object e) {
        if (!ignoreEvents) {
            setIgnoreEvents(true);
            try {
                //XXX this could be prettier...
                if (e instanceof EventObject && ((EventObject) e).getSource() instanceof Component)
                    wizardPage.userInputReceived((Component) ((EventObject) e).getSource(), e);
                else if (e instanceof TreeSelectionEvent) {
                    TreeSelectionModel mdl = (TreeSelectionModel) ((TreeSelectionEvent) e).getSource();
                    for (Object o : listenedTo)
                        if (o instanceof JTree && ((JTree) o).getSelectionModel() == mdl) {
                            wizardPage.userInputReceived((Component) o, e);
                            break;
                        }
                } else if (e instanceof DocumentEvent) {
                    Document document = ((DocumentEvent) e).getDocument();
                    for (Component o : listenedTo)
                        if (o instanceof JTextComponent && ((JTextComponent) o).getDocument() == document) {
                            wizardPage.userInputReceived((Component) o, e);
                            break;
                        }
                } else if (e instanceof ListSelectionEvent) {
                    ListSelectionModel model = (ListSelectionModel) ((ListSelectionEvent) e).getSource();
                    for (Object o : listenedTo)
                        if (o instanceof JList && ((JList<?>) o).getSelectionModel() == model) {
                            wizardPage.userInputReceived((Component) o, e);
                            break;
                        } else if (o instanceof JTable && ((JTable) o).getSelectionModel() == model) {
                            wizardPage.userInputReceived((Component) o, e);
                            break;
                        }
                } else
                    wizardPage.userInputReceived(null, e);
            } finally {
                setIgnoreEvents(false);
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        fire(e);
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (e.getSource() instanceof JComponent && "name".equals(e.getPropertyName())) {
            // Note - most components do NOT fire a property change on
            // setName(), but it is possible for this to be done intentionally
            if (e.getOldValue() instanceof String)
                wizardPage.removeFromMap(e.getOldValue());

        } else if (e.getSource() instanceof JFormattedTextField && "value".equals(e.getPropertyName())) {
            fire(e);
            wizardPage.maybeUpdateMap((JComponent) e.getSource());
        }
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        fire(e);
    }

    @Override
    public void componentAdded(ContainerEvent e) {
//        if (extListener != null && extListener.accept(e.getChild())) {
//            extListener.startListeningTo(e.getChild(), extNotifier);
//            listenedTo.add (e.getChild());
//        } else if (accept(e.getChild())) {
        if (accept(e.getChild()))
            attachTo(e.getChild());
    }

    @Override
    public void componentRemoved(ContainerEvent e) {
        if (extListener != null && extListener.accept(e.getChild())) {
            extListener.stopListeningTo(e.getChild());
            listenedTo.remove(e.getChild());
        } else if (accept(e.getChild()))
            detachFrom(e.getChild());
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        fire(e);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        fire(e);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        fire(e);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        fire(e);
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        fire(e);
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
        fire(e);
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        fire(e);
    }
}
