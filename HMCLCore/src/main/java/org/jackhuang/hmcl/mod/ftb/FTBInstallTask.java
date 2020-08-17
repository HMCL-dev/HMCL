package org.jackhuang.hmcl.mod.ftb;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FTBInstallTask extends Task<Void> {
    public static final String MODPACK_TYPE = "FTB";
    private final DefaultGameRepository repository;
    private final FTBManifest manifest;
    private final String name;
    private final File run;
    private final ModpackConfiguration<FTBManifest> config;
    private final List<Task<?>> dependents = new LinkedList<>();
    private final List<Task<?>> dependencies = new LinkedList<>();
    File json;


    public FTBInstallTask(DefaultDependencyManager dependencyManager, FTBManifest manifest) {
        this.manifest = manifest;
        this.name = manifest.modpackManifest.name;
        this.repository = dependencyManager.getGameRepository();
        this.run = repository.getRunDirectory(name);


        json = repository.getModpackConfiguration(name);
        if (repository.hasVersion(name) && !json.exists())
            throw new IllegalArgumentException("Version " + name + " already exists.");


        GameBuilder builder = dependencyManager.gameBuilder().name(name).gameVersion(splitTargets(manifest.versionManifest, TargetType.GAME).version);
        FTBVersionManifest.Targets modloader = splitTargets(manifest.versionManifest, TargetType.MODLOADER);
        builder.version(modloader.name, modloader.version);
        dependents.add(builder.buildAsync());

        onDone().register(event -> {
//            Exception ex = event.getTask().getException();
            if (event.isFailed()) {
                repository.removeVersionFromDisk(name);
            }
        });

        ModpackConfiguration<FTBManifest> config = null;
        try {
            if (json.exists()) {
                config = JsonUtils.GSON.fromJson(FileUtils.readText(json), new TypeToken<ModpackConfiguration<FTBManifest>>() {
                }.getType());

                if (!MODPACK_TYPE.equals(config.getType()))
                    throw new IllegalArgumentException("Version " + name + " is not a modpack. Cannot update this version.");
            }
        } catch (JsonParseException | IOException ignore) {
        }
        this.config = config;
        dependencies.add(new FTBCompletionTask(dependencyManager, name, manifest).withStage("hmcl.modpack.download"));
    }

    public static FTBVersionManifest.Targets splitTargets(FTBVersionManifest manifest, TargetType type) throws NoSuchElementException {
        //game version exists in "targets->(type='game'&name='minecraft')"
        Optional<FTBVersionManifest.Targets> result = manifest.targets.stream().filter(x -> x.type.equalsIgnoreCase(type.name())).findFirst();
        if (result.isPresent()) return result.get();
        throw new NoSuchElementException();
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return dependents;
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public List<String> getStages() {
        return Stream.concat(
                dependents.stream().flatMap(task -> task.getStages().stream()),
                Stream.of("hmcl.modpack", "hmcl.modpack.download")
        ).collect(Collectors.toList());
    }

    @Override
    public void execute() throws Exception {
        if (config != null) {
            // For update, remove mods not listed in new manifest
            for (FTBVersionManifest.Files old : config.getManifest().versionManifest.files) {
                if (StringUtils.isBlank(old.name)) continue;
                File oldFile = new File(run, "mods/" + old.name);
                if (!oldFile.exists()) continue;
                if (manifest.versionManifest.files.stream().noneMatch(old::equals) && !oldFile.delete())
                    throw new IOException("Unable to delete mod file " + oldFile);
            }
        }

        File root = repository.getVersionRoot(name);
        FileUtils.writeText(new File(root, "manifest.json"), JsonUtils.GSON.toJson(manifest));
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        FileUtils.writeText(json, JsonUtils.GSON.toJson(new ModpackConfiguration<>(manifest, MODPACK_TYPE, name, manifest.versionManifest.name, new ArrayList<>())));
    }

    public enum TargetType {
        GAME,
        MODLOADER
    }
}
