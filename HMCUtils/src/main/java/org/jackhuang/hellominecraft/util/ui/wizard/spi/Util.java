/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.util.ui.wizard.spi;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
final class Util {

    private Util() {
    }

    /**
     * Get an array of step ids from an array of WizardPages
     */
    static String[] getSteps(WizardPage[] pages) {
        String[] result = new String[pages.length];

        Set<String> uniqueNames = new HashSet<>(pages.length);
        for (int i = 0; i < pages.length; i++) {
            result[i] = pages[i].id();
            if (result[i] == null || uniqueNames.contains(result[i])) {
                result[i] = uniquify(getIDFromStaticMethod(pages[i].getClass()),
                        uniqueNames);
                pages[i].id = result[i];
            }
            uniqueNames.add(result[i]);
        }
        return result;
    }

    static String uniquify(String s, Set<String> used) {
        String test = s;
        if (test != null) {
            int ix = 0;
            while (used.contains(test))
                test = s + "_" + ix++;
        }
        return test;
    }

    /**
     * Get an array of descriptions from an array of WizardPages
     */
    static String[] getDescriptions(WizardPage[] pages) {
        String[] result = new String[pages.length];

        for (int i = 0; i < pages.length; i++) {
            result[i] = pages[i].description();
            if (result[i] == null)
                result[i] = getDescriptionFromStaticMethod(pages[i].getClass());
        }

        return result;
    }

    static String getIDFromStaticMethod(Class<?> clazz) {
        // System.err.println("GetID by method for " + clazz);
        String result = null;
        try {
            Method m = clazz.getDeclaredMethod("getStep", new Class<?>[]{});
            // assert m.getReturnType() == String.class;
            result = Objects.requireNonNull((String) m.invoke(clazz, (Object[]) null), "getStep may not return null");
        } catch (Exception ex) {
            //do nothing
        }
        return result == null ? clazz.getName() : result;
    }

    /**
     * Get an array of steps by looking for a static method getID() on each
     * class object passed
     */
    static String[] getSteps(Class<?>[] pages) {
        Objects.requireNonNull(pages, "Null array of classes");

        String[] result = new String[pages.length];

        Set<String> used = new HashSet<>(pages.length);
        for (int i = 0; i < pages.length; i++) {
            Objects.requireNonNull(pages[i], "Null at " + i + " in array of panel classes");

            if (!WizardPage.class.isAssignableFrom(pages[i]))
                throw new IllegalArgumentException(pages[i]
                        + " is not a subclass of WizardPage");
            result[i] = uniquify(getIDFromStaticMethod(pages[i]), used);
            if (result[i] == null)
                result[i] = pages[i].getName();
        }
        // System.err.println("Returning " + Arrays.asList(result));
        return result;
    }

//    /** Determine if a default constructor is present for a class */
//    private static boolean hasDefaultConstructor (Class clazz) {
//        try {
//            Constructor c = clazz.getConstructor(new Class[0]);
//            return c != null;
//        } catch (Exception e) {
//            return false;
//        }
//    }
    /**
     * Get an array of descriptions by looking for the static method
     * getDescription() on each passed class object
     */
    static String[] getDescriptions(Class<?>[] pages) {
        String[] result = new String[pages.length];

        for (int i = 0; i < pages.length; i++)
            result[i] = getDescriptionFromStaticMethod(pages[i]);

        return result;
    }

    static String getDescriptionFromStaticMethod(Class<?> clazz) {
        Method m;
        try {
            m = clazz.getDeclaredMethod("getDescription", (Class[]) null);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new IllegalArgumentException("Could not find or access "
                    + "public static String " + clazz.getName()
                    + ".getDescription() - make sure it exists");
        }

        if (m.getReturnType() != String.class)
            throw new IllegalArgumentException("getStep has wrong "
                    + " return type: " + m.getReturnType() + " on "
                    + clazz);

        if (!Modifier.isStatic(m.getModifiers()))
            throw new IllegalArgumentException("getStep is not "
                    + "static on " + clazz);

        return AccessController.doPrivileged((PrivilegedAction<String>) () -> {
            try {
                m.setAccessible(true);
                return (String) m.invoke(null, (Object[]) null);
            } catch (InvocationTargetException | IllegalAccessException ite) {
                throw new IllegalArgumentException("Could not invoke "
                        + "public static String " + clazz.getName()
                        + ".getDescription() - make sure it exists.", ite);
            }
        });
    }
}
