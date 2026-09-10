/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.terracotta.profile;

import com.google.gson.annotations.SerializedName;

public final class TerracottaProfile {
    @SerializedName("machine_id")
    private final String machineID;

    @SerializedName("name")
    private final String name;

    @SerializedName("vendor")
    private final String vendor;

    @SerializedName("kind")
    private final ProfileKind type;

    private TerracottaProfile(String machineID, String name, String vendor, ProfileKind type) {
        this.machineID = machineID;
        this.name = name;
        this.vendor = vendor;
        this.type = type;
    }

    public String getMachineID() {
        return machineID;
    }

    public String getName() {
        return name;
    }

    public String getVendor() {
        return vendor;
    }

    public ProfileKind getType() {
        return type;
    }
}
