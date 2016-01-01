/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.utilities;

import java.util.ResourceBundle;

/**
 *
 * @author hyh
 */
public final class C {
    
    public static final ResourceBundle I18N = ResourceBundle.getBundle("org/jackhuang/hellominecraftlauncher/I18N");
    
    public static final String[] URL_LIBRARIES = new String[]{"https://libraries.minecraft.net/", "http://bmclapi.bangbang93.com/mclibraries/"};
    public static final String[] URL_VERSIONS = new String[]{"https://s3.amazonaws.com/Minecraft.Download/versions/", "http://bmclapi.bangbang93.com/mcversion/"};
    public static final String[] URL_INDEXES = new String[]{"https://s3.amazonaws.com/Minecraft.Download/indexes/", "http://bmclapi.bangbang93.com/mcindexes/"};
    public static final String[] URL_VERSIONLIST = new String[]{"https://s3.amazonaws.com/Minecraft.Download/versions/versions.json", "http://bmclapi.bangbang93.com/mcversions/versions.json"};
    public static final String[] URL_OPTIFINE = new String[]{"http://optifine.net/downloads.php", "http://bmclapi.bangbang93.com/optifine/versionlist"};
    public static final String URL_FORGE_BMCL_LEGACY = "http://bmclapi.bangbang93.com/forge/legacylist";
    public static final String URL_FORGE_BMCL_NEW = "http://bmclapi.bangbang93.com/forge/versionlist";
    public static final String URL_INTEGRATE = "http://hmcl.googlecode.com/svn/trunk/integrate-packages/";
    
    public static final String FILE_PLUGINS = "plugins";
    public static final String URL_PUBLISH = "http://www.mcbbs.net/thread-142335-1-1.html";
    
    public static final String URL_ASSETS_MOJANG = "http://resources.download.minecraft.net/";
    public static final String URL_ASSETS_BMCL = "http://bmclapi.bangbang93.com/mcassets/";
    public static final String URL_OLD_ASSETS_BMCL = "http://www.bangbang93.com/bmcl/resources/";
    
    private C(){}
    
}
