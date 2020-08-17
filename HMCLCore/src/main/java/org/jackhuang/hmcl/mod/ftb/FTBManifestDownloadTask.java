package org.jackhuang.hmcl.mod.ftb;

import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.util.Collection;
import java.util.LinkedList;

public class FTBManifestDownloadTask extends Task<FTBManifest> {
    private final Collection<Task<?>> dependencies = new LinkedList<>();
    private final Task<String> p;
    private final Task<String> v;

    public FTBManifestDownloadTask(String pack, String version) {
        String API_ROOT = "https://api.modpacks.ch/public/modpack/";
        p = new GetTask(NetworkUtils.toURL(API_ROOT + pack)).setName("Modpack Manifest");
        v = new GetTask(NetworkUtils.toURL(API_ROOT + pack + "/" + version)).setName("Version Manifest");
        dependencies.add(p);
        dependencies.add(v);
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void execute() throws Exception {

    }

    @Override
    public void postExecute() {
        this.setResult(new FTBManifest(
                JsonUtils.fromMaybeMalformedJson(p.getResult(), FTBModpackManifest.class),
                JsonUtils.fromMaybeMalformedJson(v.getResult(), FTBVersionManifest.class))
        );
    }
}
