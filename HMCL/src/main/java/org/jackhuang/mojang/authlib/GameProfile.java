package org.jackhuang.mojang.authlib;

import org.jackhuang.mojang.authlib.properties.PropertyMap;
import java.util.UUID;
import org.jackhuang.hellominecraft.utils.StrUtils;

public class GameProfile {

    private final UUID id;
    private final String name;
    private final PropertyMap properties = new PropertyMap();
    private boolean legacy;

    public GameProfile(UUID id, String name) {
	if ((id == null) && (StrUtils.isBlank(name))) {
	    throw new IllegalArgumentException("Name and ID cannot both be blank");
	}

	this.id = id;
	this.name = name;
    }

    public UUID getId() {
	return this.id;
    }

    public String getName() {
	return this.name;
    }

    public PropertyMap getProperties() {
	return this.properties;
    }

    public boolean isComplete() {
	return (this.id != null) && (StrUtils.isNotBlank(getName()));
    }

    @Override
    public boolean equals(Object o) {
	if (this == o) {
	    return true;
	}
	if ((o == null) || (getClass() != o.getClass())) {
	    return false;
	}

	GameProfile that = (GameProfile) o;

	if (this.id != null ? !this.id.equals(that.id) : that.id != null) {
	    return false;
	}
	return this.name != null ? this.name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
	int result = this.id != null ? this.id.hashCode() : 0;
	result = 31 * result + (this.name != null ? this.name.hashCode() : 0);
	return result;
    }

    @Override
    public String toString() {
	return "GameProfile{" + "id=" + id + ", name=" + name + ", properties=" + properties + ", legacy=" + legacy + '}';
    }


    public boolean isLegacy() {
	return this.legacy;
    }
}