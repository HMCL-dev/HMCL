package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil;

import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.properties.PropertyMap;
import java.util.UUID;
import org.jackhuang.hellominecraft.utils.StrUtils;

public class GameProfile {

    public final UUID id;
    public final String name;
    public final PropertyMap properties = new PropertyMap();

    public GameProfile(UUID id, String name) {
        if ((id == null) && (StrUtils.isBlank(name)))
            throw new IllegalArgumentException("Name and ID cannot both be blank");

        this.id = id;
        this.name = name;
    }

    public boolean isComplete() {
        return (id != null) && (StrUtils.isNotBlank(name));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if ((o == null) || (getClass() != o.getClass()))
            return false;

        GameProfile that = (GameProfile) o;

        if (id != null ? !id.equals(that.id) : that.id != null)
            return false;
        return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "GameProfile{" + "id=" + id + ", name=" + name + ", properties=" + properties + '}';
    }
}
