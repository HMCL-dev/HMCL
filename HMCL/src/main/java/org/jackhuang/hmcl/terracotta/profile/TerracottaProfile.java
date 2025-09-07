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
