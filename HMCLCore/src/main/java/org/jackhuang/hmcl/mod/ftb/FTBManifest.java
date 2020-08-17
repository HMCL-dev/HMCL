package org.jackhuang.hmcl.mod.ftb;

public class FTBManifest {
    public FTBVersionManifest versionManifest;
    public FTBModpackManifest modpackManifest;

    public FTBManifest(FTBModpackManifest modpackManifest, FTBVersionManifest versionManifest) {
        this.versionManifest = versionManifest;
        this.modpackManifest = modpackManifest;
    }
}
