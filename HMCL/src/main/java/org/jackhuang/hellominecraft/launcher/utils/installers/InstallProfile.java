/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.launcher.utils.installers;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hellominecraft.launcher.utils.version.MinecraftVersion;
import org.jackhuang.hellominecraft.launcher.utils.installers.forge.Install;

/**
 *
 * @author hyh
 */
public class InstallProfile {
    @SerializedName("install")
    public Install install;
    @SerializedName("versionInfo")
    public MinecraftVersion versionInfo;
}
