/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.assets;

/**
 *
 * @author hyh
 */
public class AssetsObject {
    private String hash;
    private long size;

    public AssetsObject() {
    }

    public String getHash() {
	return this.hash;
    }

    public long getSize() {
	return this.size;
    }

    @Override
    public boolean equals(Object o) {
	if (this == o) return true;
	if (o == null || getClass() != o.getClass()) return false;
	AssetsObject that = (AssetsObject) o;
	if (this.size != that.size) return false;
	return this.hash.equals(that.hash);
    }

    @Override
    public int hashCode() {
	int result = this.hash.hashCode();
	result = 31 * result + (int) (this.size ^ this.size >>> 32);
	return result;
    }
}
