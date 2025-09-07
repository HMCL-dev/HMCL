package org.jackhuang.hmcl.terracotta.profile;

import com.google.gson.annotations.SerializedName;

public enum ProfileKind {
    @SerializedName("HOST")
    HOST,
    @SerializedName("LOCAL")
    LOCAL,
    @SerializedName("GUEST")
    GUEST
}
