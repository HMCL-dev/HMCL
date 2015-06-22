/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.installers;

import java.util.Comparator;
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionList.InstallerVersion;

/**
 *
 * @author hyh
 */
public class InstallerVersionNewerComparator implements Comparator<InstallerVersion> {

    @Override
    public int compare(InstallerVersion o1, InstallerVersion o2) {
        return -o1.compareTo(o2);
    }
}
