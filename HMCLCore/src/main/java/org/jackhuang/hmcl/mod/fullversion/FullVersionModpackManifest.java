package org.jackhuang.hmcl.mod.fullversion;

import org.jackhuang.hmcl.mod.ModpackManifest;
import org.jackhuang.hmcl.mod.ModpackProvider;

public class FullVersionModpackManifest implements ModpackManifest {
    @Override
    public ModpackProvider getProvider() {
        return FullVersionModpackProvider.INSTANCE;
    }
}
