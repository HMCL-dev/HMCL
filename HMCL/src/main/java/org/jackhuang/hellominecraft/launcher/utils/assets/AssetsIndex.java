/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.assets;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author hyh
 */
public class AssetsIndex {

    public static final String DEFAULT_ASSET_NAME = "legacy";
    private Map<String, AssetsObject> objects;
    private boolean virtual;
    
    public AssetsIndex() {
	this.objects = new LinkedHashMap();
    }

    public Map<String, AssetsObject> getFileMap() {
	return this.objects;
    }

    public Set<AssetsObject> getUniqueObjects() {
	return new HashSet(this.objects.values());
    }

    public boolean isVirtual() {
	return this.virtual;
    }
}
