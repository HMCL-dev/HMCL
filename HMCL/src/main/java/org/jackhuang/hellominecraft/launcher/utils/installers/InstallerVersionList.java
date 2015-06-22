/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.installers;

import java.util.Comparator;
import java.util.List;
import org.jackhuang.hellominecraft.utils.functions.Consumer;

/**
 *
 * @author hyh
 */
public abstract class InstallerVersionList implements Consumer<String[]> {
    /**
     * Refresh installer versions list from the downloaded content.
     */
    public abstract void refreshList(String[] versions) throws Exception;
    public abstract String getName();
    public abstract List<InstallerVersion> getVersions(String mcVersion);
    
    public static class InstallerVersion implements Comparable<InstallerVersion> {
	public String selfVersion, mcVersion;
	public String installer, universal;
        public String changelog;

	public InstallerVersion(String selfVersion, String mcVersion) {
	    this.selfVersion = selfVersion;
	    this.mcVersion = mcVersion;
	}

        @Override
        public int compareTo(InstallerVersion o) {
            return selfVersion.compareTo(o.selfVersion);
        }
    }
    
    public static class InstallerVersionComparator implements Comparator<InstallerVersion> {
        public static final InstallerVersionComparator INSTANCE = new InstallerVersionComparator();
        @Override
        public int compare(InstallerVersion o1, InstallerVersion o2) {
            return o2.compareTo(o1);
        }
    }

    @Override
    public void accept(String[] v) {
        try {
            refreshList(v);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
