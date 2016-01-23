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
package org.jackhuang.hellominecraft.utils.views.wizard.modules;

import java.util.ResourceBundle;

/**
 * Non API class for accessing a few things in NetBeans via reflection.
 *
 * @author Tim Boudreau
 */
public final class NbBridge {

    private NbBridge() {
    }

    public static String getString(String path, Class callerType, String key) {
        return getStringViaResourceBundle(path, key);
    }

    private static String getStringViaResourceBundle(String path, String key) {
        return ResourceBundle.getBundle(path).getString(key);
    }
}
