/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public abstract class DefaultGameRepository implements GameRepository {

    private static final ExecutorService POOL = Lang.threadPool("DefaultGameRepository", true, 4, 10, TimeUnit.SECONDS);

    private static final GameInstanceManifest CLASSIC_MANIFEST = new GameInstanceManifest(
            new GameInstanceID("Classic"),
            "${auth_player_name} ${auth_session} --workDir ${game_directory}",
            null,
            "net.minecraft.client.Minecraft",
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(
                    classicLibrary("lwjgl"),
                    classicLibrary("jinput"),
                    classicLibrary("lwjgl_util")),
            null,
            null,
            null,
            ReleaseType.UNKNOWN,
            null,
            null,
            0,
            false,
            false,
            null,
            null
    );

    private static Library classicLibrary(String name) {
        return new Library(new Artifact("", "", ""), null,
                new LibrariesDownloadInfo(new LibraryDownloadInfo("bin/" + name + ".jar"), null),
                null, null, null, null, null, null);
    }

    private static boolean hasClassicInstance(Path baseDirectory) {
        Path bin = baseDirectory.resolve("bin");
        return Files.isDirectory(bin)
                && Files.exists(bin.resolve("lwjgl.jar"))
                && Files.exists(bin.resolve("jinput.jar"))
                && Files.exists(bin.resolve("lwjgl_util.jar"));
    }

    /// Published snapshot, always updated on the JavaFX application thread when the toolkit is live.
    private final ObjectProperty<DefaultGameRepositorySnapshot> snapshot;

    /// Atomically holds the repository's sole open draft, or `null` when no draft is active.
    private final AtomicReference<@Nullable DefaultGameRepositoryDraft> activeDraft = new AtomicReference<>();

    /// Whether at least one full refresh has completed since the base directory was set.
    private volatile boolean loaded;

    /// Creates a repository rooted at the given directory with an empty initial snapshot.
    ///
    /// @param baseDirectory the initial repository base directory
    public DefaultGameRepository(Path baseDirectory) {
        DefaultGameRepositorySnapshot initial = createSnapshot(createLayout(baseDirectory));
        initial.seal();
        this.snapshot = new SimpleObjectProperty<>(initial);
    }

    /// Creates the repository layout rooted at the given directory.
    ///
    /// @param baseDirectory the repository base directory
    /// @return the layout used by this repository
    protected abstract DefaultGameRepositoryLayout createLayout(Path baseDirectory);

    /// Prepares subclass-managed writes before instance files are moved or removed.
    ///
    /// The default implementation does nothing.
    ///
    /// @throws IOException if pending writes cannot be completed
    protected void flushPendingInstanceWrites() throws IOException {
    }

    /// Replaces the repository layout with an empty snapshot rooted at `baseDirectory`.
    ///
    /// @param baseDirectory the new repository base directory
    /// @throws IllegalStateException if a draft is active
    public void setBaseDirectory(Path baseDirectory) {
        checkNoActiveDraft("set base directory");
        // Mark unloaded before publishing so snapshot listeners do not treat the empty snapshot as ready.
        this.loaded = false;
        DefaultGameRepositorySnapshot initial = createSnapshot(createLayout(baseDirectory));
        publishSnapshot(initial);
    }

    /// {@inheritDoc}
    ///
    /// The returned snapshot is sealed and must not be modified. Normal writers must use
    /// [#openDraft()]; refresh and layout replacement use the internal publication path.
    @Override
    public DefaultGameRepositorySnapshot getSnapshot() {
        return snapshot.get();
    }

    /// Returns a read-only view of the current published snapshot for JavaFX bindings.
    ///
    /// The property is the sole holder of the published snapshot. Updates are applied on the JavaFX
    /// application thread so listeners may safely touch the scene graph.
    ///
    /// @return the observable snapshot property
    public ReadOnlyObjectProperty<? extends DefaultGameRepositorySnapshot> snapshotProperty() {
        return snapshot;
    }

    /// Seals `newSnapshot` if needed and publishes it as the current repository snapshot.
    ///
    /// This is the low-level publication mechanism for draft commit, refresh, and layout
    /// replacement. Other repository writes must use [#openDraft()].
    ///
    /// When the JavaFX toolkit is running, the property is updated on the JavaFX application thread
    /// (blocking the caller if publish happens off the FX thread) so that listeners run on FX and
    /// [#getSnapshot()] observes the new value before this method returns.
    ///
    /// @param newSnapshot the snapshot to publish; must not already be visible as [#getSnapshot()]
    ///                    unless it is a freshly built replacement
    protected void publishSnapshot(DefaultGameRepositorySnapshot newSnapshot) {
        newSnapshot.seal();
        runOnFxThreadAndWait(() -> {
            snapshot.set(newSnapshot);
        });
    }

    /// Publishes the immutable successor snapshot of the repository's active draft.
    ///
    /// @param draft       the active draft
    /// @param newSnapshot the draft's successor snapshot
    /// @throws IllegalStateException if `draft` is not the active draft
    void publishDraftSnapshot(
            DefaultGameRepositoryDraft draft,
            DefaultGameRepositorySnapshot newSnapshot) {
        checkActiveDraft(draft);
        publishSnapshot(newSnapshot);
    }

    /// Verifies that `draft` owns this repository's exclusive write session.
    ///
    /// @param draft the draft to verify
    /// @throws IllegalStateException if `draft` is not active
    void checkActiveDraft(DefaultGameRepositoryDraft draft) {
        if (activeDraft.get() != draft) {
            throw new IllegalStateException("Draft is not the active repository draft");
        }
    }

    /// Releases the exclusive write session owned by `draft`.
    ///
    /// @param draft the draft that completed, aborted, or failed
    /// @throws IllegalStateException if `draft` is not active
    void releaseDraft(DefaultGameRepositoryDraft draft) {
        if (!activeDraft.compareAndSet(draft, null)) {
            throw new IllegalStateException("Draft is not the active repository draft");
        }
    }

    /// Runs an action on the JavaFX application thread and waits for its completion.
    ///
    /// The action runs on the calling thread when the JavaFX toolkit has not been initialized.
    /// Interruptions are restored after a queued JavaFX action completes.
    ///
    /// @param action the action to run
    private static void runOnFxThreadAndWait(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }

        CountDownLatch completed = new CountDownLatch(1);
        try {
            Platform.runLater(() -> {
                try {
                    action.run();
                } finally {
                    completed.countDown();
                }
            });
        } catch (IllegalStateException ignored) {
            // JavaFX toolkit is not initialized (for example in headless unit tests).
            action.run();
            return;
        }

        boolean interrupted = false;
        while (true) {
            try {
                completed.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public DefaultGameRepositoryLayout getLayout() {
        return getSnapshot().getLayout();
    }

    public boolean isLoaded() {
        return loaded;
    }

    /// {@inheritDoc}
    ///
    /// @throws IllegalStateException if a draft is active
    @Override
    public void refresh() {
        checkNoActiveDraft("refresh");
        refreshRepository();
    }

    /// Reloads and publishes repository state while the caller owns the direct-write session.
    private void refreshRepository() {
        DefaultGameRepositorySnapshot newSnapshot = createSnapshot(getSnapshot().getLayout());
        DefaultGameRepositoryLayout layout = newSnapshot.getLayout();

        if (hasClassicInstance(layout.getBaseDirectory())) {
            GameInstanceID id = CLASSIC_MANIFEST.id();
            newSnapshot.put(createInstance(newSnapshot, id, CLASSIC_MANIFEST));
        }

        Path instancesDir = layout.getBaseDirectory().resolve("versions");
        if (Files.isDirectory(instancesDir)) {
            try (Stream<Path> stream = Files.list(instancesDir)) {
                List<CompletableFuture<@Nullable DefaultGameInstance>> futures = stream
                        .filter(Files::isDirectory)
                        .map(dir -> CompletableFuture.supplyAsync(
                                Lang.wrap(() -> loadInstanceDirectory(newSnapshot, dir)),
                                POOL))
                        .toList();

                for (CompletableFuture<@Nullable DefaultGameInstance> future : futures) {
                    try {
                        DefaultGameInstance instance = future.join();
                        if (instance != null) {
                            newSnapshot.put(instance);
                        }
                    } catch (Exception e) {
                        LOG.warning("Failed to load instance", e);
                    }
                }

                newSnapshot.removeInvalidInstances();
            } catch (IOException e) {
                LOG.warning("Failed to load instance from " + instancesDir, e);
            }
        }

        // Mark loaded before publishing so snapshot listeners observe a ready repository.
        loaded = true;
        publishSnapshot(newSnapshot);
    }

    /// Loads one instance directory without renaming on-disk JSON or jar files.
    ///
    /// When the conventional `versions/<id>/<id>.json` is missing but the directory contains exactly
    /// one JSON file, that manifest path is recorded on the instance. The primary jar is derived as
    /// the sibling path with the same base name.
    ///
    /// @param snapshot the unsealed snapshot that will own the instance
    /// @param dir      the instance directory under `versions/`
    /// @return the loaded instance, or `null` when the directory should be ignored
    private @Nullable DefaultGameInstance loadInstanceDirectory(DefaultGameRepositorySnapshot snapshot, Path dir) {
        GameInstanceID id;
        try {
            id = new GameInstanceID(FileUtils.getName(dir));
        } catch (IllegalArgumentException e) {
            LOG.warning("Ignoring instance directory with invalid id " + dir, e);
            return null;
        }

        DefaultGameRepositoryLayout layout = snapshot.getLayout();
        Path conventionalJson = layout.getInstanceJson(id);

        Path json;
        @Nullable Path manifestFileOverride = null;

        if (Files.isRegularFile(conventionalJson)) {
            json = conventionalJson;
        } else {
            List<Path> jsons = FileUtils.listFilesByExtension(dir, "json");
            if (jsons.size() != 1) {
                LOG.info("No available json file found, ignoring instance " + id);
                return null;
            }

            json = jsons.get(0);
            if (!json.equals(conventionalJson)) {
                manifestFileOverride = json;
            }

            LOG.info("Using non-conventional instance manifest for " + id + ": " + json);
        }

        GameInstanceManifest manifest;
        try {
            manifest = readInstanceManifest(json);
        } catch (Exception e) {
            LOG.warning("Malformed instance json " + id + " (" + json + ")", e);
            return null;
        }

        // Directory name is the repository identity; keep the on-disk files untouched.
        if (!id.equals(manifest.id())) {
            manifest = manifest.withId(id);
        }

        return createInstance(snapshot, id, manifest, manifestFileOverride);
    }

    private static GameInstanceManifest readInstanceManifest(Path json) throws IOException, JsonParseException {
        GameInstanceManifest manifest = JsonUtils.fromJsonFile(json, GameInstanceManifest.class);
        if (manifest == null) {
            throw new JsonParseException("Manifest is null");
        }
        return manifest;
    }

    static void moveInstanceFiles(Path baseDirectory, GameInstanceID from, GameInstanceID to) throws IOException {
        Path instancesDir = baseDirectory.resolve("versions");
        Path fromDir = instancesDir.resolve(from.id());
        Path toDir = instancesDir.resolve(to.id());
        Files.move(fromDir, toDir);

        Path fromJson = toDir.resolve(from + ".json");
        Path fromJar = toDir.resolve(from + ".jar");
        Path toJson = toDir.resolve(to + ".json");
        Path toJar = toDir.resolve(to + ".jar");

        boolean hasJarFile = Files.exists(fromJar);

        try {
            Files.move(fromJson, toJson);
            if (hasJarFile) {
                Files.move(fromJar, toJar);
            }
        } catch (IOException e) {
            try {
                Files.move(toJson, fromJson);
            } catch (Throwable e2) {
                e.addSuppressed(e2);
            }

            if (hasJarFile) {
                try {
                    Files.move(toJar, fromJar);
                } catch (Throwable e2) {
                    e.addSuppressed(e2);
                }
            }

            try {
                Files.move(toDir, fromDir);
            } catch (Exception e2) {
                e.addSuppressed(e2);
            }
            throw e;
        }
    }

    @Override
    public DefaultGameInstance getInstance(GameInstanceID id) throws NoSuchGameInstanceException {
        return getSnapshot().getRegistered(id);
    }

    @Override
    public boolean renameInstance(GameInstanceID from, GameInstanceID to) {
        try (DefaultGameRepositoryDraft draft = openDraft()) {
            draft.rename(from, to);
            draft.commit();
            return true;
        } catch (IOException | JsonParseException | NoSuchGameInstanceException | IllegalArgumentException e) {
            LOG.warning("Unable to rename instance " + from + " to " + to, e);
            return false;
        }
    }

    /// Removes an instance from the published index and attempts to remove its backing directory.
    ///
    /// Registered instances are removed through an exclusive draft: their roots are first moved to
    /// draft-private storage, the new snapshot is published once, and the staged roots are then
    /// deleted. An unregistered orphan directory uses the legacy trash-or-delete cleanup path and is
    /// followed by a repository refresh.
    ///
    /// @param id the instance id
    /// @return `false` if removal is denied or the instance directory cannot be staged; `true` if
    ///         the directory is absent or staging succeeds
    public boolean removeInstanceFromDisk(GameInstanceID id) {
        if (getSnapshot().get(id) != null) {
            try (DefaultGameRepositoryDraft draft = openDraft()) {
                draft.remove(id);
                draft.commit();
                return true;
            } catch (IOException e) {
                LOG.warning("Unable to remove instance " + id, e);
                return false;
            }
        }

        checkNoActiveDraft("remove instance");
        try {
            Path file = getLayout().getInstanceRoot(id);
            if (Files.notExists(file)) {
                return true;
            }

            Path removedFile = file.toAbsolutePath().resolveSibling(FileUtils.getName(file) + "_removed");
            try {
                Files.move(file, removedFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOG.warning("Unable to remove instance directory: " + file, e);
                return false;
            }

            if (FileUtils.moveToTrash(removedFile)) {
                return true;
            }

            for (Path path : FileUtils.listFilesByExtension(removedFile, "json")) {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    LOG.warning("Failed to delete file " + path, e);
                }
            }

            try {
                FileUtils.deleteDirectory(removedFile);
            } catch (IOException e) {
                LOG.warning("Unable to remove instance directory: " + removedFile, e);
            }
            return true;
        } finally {
            refreshRepository();
        }
    }

    /// Opens a draft for staging instance index changes and committing them once.
    ///
    /// @return a new open draft
    @Override
    public DefaultGameRepositoryDraft openDraft() {
        DefaultGameRepositoryDraft draft = new DefaultGameRepositoryDraft(this);
        if (!activeDraft.compareAndSet(null, draft)) {
            throw new IllegalStateException("Another repository draft is already open");
        }
        return draft;
    }

    /// Writes a stored manifest and publishes a new snapshot in a single draft commit.
    ///
    /// @param instanceManifest the persistent manifest to save
    /// @return the saved manifest
    /// @throws IOException if the manifest cannot be written
    public GameInstanceManifest save(GameInstanceManifest instanceManifest) throws IOException {
        try (DefaultGameRepositoryDraft draft = openDraft()) {
            draft.put(instanceManifest);
            draft.commit();
        }
        return instanceManifest;
    }

    /// Saves a stored manifest without applying derived launch-view normalization.
    ///
    /// The returned task writes the manifest and publishes a snapshot containing exactly that
    /// persistent representation, including its inheritance and pending patches.
    ///
    /// @param instanceManifest the persistent manifest to save
    /// @return the task that saves and publishes the manifest
    public Task<GameInstanceManifest> saveAsync(GameInstanceManifest instanceManifest) {
        return Task.supplyAsync(() -> save(instanceManifest));
    }

    /// Creates a task that updates one registered instance inside an exclusive draft.
    ///
    /// The updater receives the instance from the immutable published snapshot and must return a
    /// working manifest with the same id. Its result is staged and committed exactly once. Failure
    /// or cancellation aborts the draft; shared cache files written by the updater are retained.
    ///
    /// @param <E>        the checked exception type thrown while creating the update task
    /// @param instanceId the instance to update
    /// @param updater    the asynchronous manifest update
    /// @return the task that commits the updated manifest
    public <E extends Exception> Task<Void> updateInstanceAsync(
            GameInstanceID instanceId,
            ExceptionalFunction<GameInstance, Task<GameInstanceManifest>, E> updater) {
        return Task.composeAsync(() -> {
            DefaultGameRepositoryDraft draft = openDraft();
            try {
                return Objects.requireNonNull(
                                updater.apply(draft.getBaseSnapshot().getInstance(instanceId)),
                                "Instance updater returned null")
                        .thenApplyAsync(manifest -> {
                            if (!instanceId.equals(manifest.id())) {
                                throw new IllegalArgumentException(
                                        "Instance updater changed id from " + instanceId + " to " + manifest.id());
                            }
                            draft.put(manifest);
                            draft.commit();
                            return manifest;
                        }).whenComplete(exception -> {
                            if (draft.isOpen()) {
                                draft.abort();
                            }
                        });
            } catch (Throwable exception) {
                abortDraftAfterFailure(draft, exception);
                throw exception;
            }
        });
    }

    /// Aborts a draft after task construction fails and preserves any cleanup failure.
    ///
    /// @param draft   the draft to abort
    /// @param failure the failure that prevented task construction
    private static void abortDraftAfterFailure(DefaultGameRepositoryDraft draft, Throwable failure) {
        try {
            draft.abort();
        } catch (Exception cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    /// Creates an empty unsealed snapshot for the given layout.
    ///
    /// @param layout the layout for the new snapshot
    /// @return a new unsealed snapshot
    protected DefaultGameRepositorySnapshot createSnapshot(DefaultGameRepositoryLayout layout) {
        return new DefaultGameRepositorySnapshot(this, layout);
    }

    /// Creates a conventional instance with layout-default storage paths.
    ///
    /// @param snapshot the snapshot that will own the instance
    /// @param id       the instance id
    /// @param manifest the stored instance manifest
    /// @return the new instance
    protected final DefaultGameInstance createInstance(
            DefaultGameRepositorySnapshot snapshot,
            GameInstanceID id,
            GameInstanceManifest manifest) {
        return createInstance(snapshot, id, manifest, null);
    }

    /// Creates an instance, optionally recording a non-conventional manifest path.
    ///
    /// @param snapshot     the snapshot that will own the instance
    /// @param id           the instance id
    /// @param manifest     the stored instance manifest
    /// @param manifestFile the actual manifest JSON path, or `null` for the layout default
    /// @return the new instance
    protected abstract DefaultGameInstance createInstance(
            DefaultGameRepositorySnapshot snapshot,
            GameInstanceID id,
            GameInstanceManifest manifest,
            @Nullable Path manifestFile);

    /// Verifies that no draft is currently active.
    ///
    /// @param operation operation rejected when a draft is active
    /// @throws IllegalStateException if a draft is active
    private void checkNoActiveDraft(String operation) {
        if (activeDraft.get() != null) {
            throw new IllegalStateException("Repository has an open draft; cannot " + operation);
        }
    }

}
