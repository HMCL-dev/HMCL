/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.installer.cauldron;

/**
 *
 * @author hyh
 */
public class ForgeVersion {
    public String vername, ver, mcver, releasetime, changelog;
    public String[] installer, javadoc, src, universal, userdev;
    public int typeint;

    @Override
    public String toString() {
	return "ForgeVersion{" + "vername=" + vername + ", ver=" + ver + ", mcver=" + mcver + ", releasetime=" + releasetime + ", changelog=" + changelog + ", installer=" + installer + ", javadoc=" + javadoc + ", src=" + src + ", universal=" + universal + ", userdev=" + userdev + ", typeint=" + typeint + '}';
    }
}
