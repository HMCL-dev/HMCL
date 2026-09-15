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
package org.jackhuang.hmcl.modpack.curse;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Resolves the CurseForge metadata a modpack completion run needs.
///
/// The lookups below depend on nothing but the manifest, so this task is scheduled beside the game
/// download and the unpacking rather than after them. They are round-trip bound rather than
/// bandwidth bound, so sharing the link with a download that saturates it costs nothing, whereas
/// running them afterwards puts every one of those round trips on the critical path.
///
/// The task reports no progress: it runs underneath whichever stage is currently reporting, and a
/// second counter feeding the same stage would only make that progress overshoot.
@NotNullByDefault
public final class CurseManifestLookupTask extends Task<CurseManifestLookup> {

    /// The manifest entries to resolve.
    private final List<CurseManifestFile> files;

    /// Whether every entry's name could be resolved.
    private final AtomicBoolean allNameKnown = new AtomicBoolean(true);

    /// Whether some entry refers to a file CurseForge no longer serves.
    private final AtomicBoolean notFound = new AtomicBoolean(false);

    /// Creates a task resolving the metadata needed by a completion run.
    ///
    /// @param files the manifest entries, or `null` when the manifest carries none
    public CurseManifestLookupTask(List<CurseManifestFile> files) {
        this.files = files == null ? List.of() : files;
    }

    @Override
    public void execute() throws Exception {
        if (files.isEmpty()) {
            setResult(new CurseManifestLookup(files, Map.of(), true, false));
            return;
        }

        // Neither lookup depends on the other: the class id batch is keyed by project id, which is
        // known before a single file name has been resolved, and resolving a name never changes a
        // project id. They are dispatched together because both are latency bound and draw on the
        // same CurseForge request budget, so running them one after the other left that budget
        // almost entirely unused while the shorter of the two was in flight.
        ExecutorService executor = Schedulers.io();
        Future<List<CurseManifestFile>> resolvedNames = executor.submit(() -> resolveFileNames(files));
        Future<Map<String, Integer>> classIdLookup = executor.submit(() -> loadAddonClassIds(files));

        List<CurseManifestFile> resolved = await(resolvedNames);
        Map<String, Integer> addonClassIds = await(classIdLookup);

        setResult(new CurseManifestLookup(resolved, addonClassIds, allNameKnown.get(), notFound.get()));
    }

    /// Resolves the metadata without a task, for a caller with nowhere to schedule one.
    ///
    /// @param files the manifest entries, or `null` when the manifest carries none
    /// @return the resolved metadata
    /// @throws Exception the failure the lookups reported
    static CurseManifestLookup resolve(List<CurseManifestFile> files) throws Exception {
        CurseManifestLookupTask task = new CurseManifestLookupTask(files);
        task.execute();
        return task.getResult();
    }

    /// Fills in the file name, url and hashes of every manifest entry that is missing them.
    ///
    /// The entries that need it are answered by one batch request, because a modpack manifest names
    /// the exact file every entry pins and the batch endpoint takes those file ids together. Only
    /// what that request leaves out costs a `GET /v1/mods/{projectId}/files/{fileId}` of its own;
    /// those requests are independent, so they are dispatched to [Schedulers#io()] rather than spread
    /// over the common pool, whose parallelism is capped at the processor count and left most of each
    /// request's latency uncovered. [CurseForgeRemoteAddonRepository] still caps how many are in
    /// flight.
    ///
    /// Order is preserved, and a failing entry is logged and returned unchanged, so one unreachable
    /// file does not abort the completion.
    ///
    /// @return one entry per input, in the same order
    private List<CurseManifestFile> resolveFileNames(List<CurseManifestFile> files) throws Exception {
        if (files.isEmpty())
            return files;

        Map<String, RemoteAddon.File> batched = resolveBatched(files);
        CurseManifestFile[] resolved = new CurseManifestFile[files.size()];
        ExecutorService executor = Schedulers.io();

        List<Future<?>> pending = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            int index = i;
            CurseManifestFile file = files.get(i);

            if (!needsResolution(file)) {
                resolved[index] = file;
                continue;
            }

            @Nullable RemoteAddon.File known = batched.get(Integer.toString(file.fileID()));
            if (known != null) {
                resolved[index] = applyRemote(file, known);
            } else {
                pending.add(executor.submit(() -> resolved[index] = resolveFileName(file)));
            }
        }

        for (Future<?> future : pending) {
            await(future);
        }

        return Arrays.asList(resolved);
    }

    /// Returns whether an entry still needs its file name, url and hashes filled in.
    ///
    /// @param file the manifest entry
    /// @return `true` when CurseForge still has to be asked about it
    private static boolean needsResolution(CurseManifestFile file) {
        return StringUtils.isBlank(file.fileName()) || file.url() == null;
    }

    /// Copies the remote file metadata onto a manifest entry.
    ///
    /// @param file   the manifest entry
    /// @param remote the remote file the entry pins
    /// @return the entry enriched with the remote file name, url and hashes
    private static CurseManifestFile applyRemote(CurseManifestFile file, RemoteAddon.File remote) {
        return file
                .withFileName(remote.filename())
                .withURL(remote.url())
                .withHashes(remote.hashes());
    }

    /// Resolves every entry that needs it in one batch request.
    ///
    /// A failure is not fatal: entries the batch does not answer for fall back to the per-entry
    /// request in [#resolveFileName].
    ///
    /// @param files the manifest entries
    /// @return the file of every entry the server answered for, keyed by file id
    private Map<String, RemoteAddon.File> resolveBatched(List<CurseManifestFile> files) {
        List<String> ids = files.stream()
                .filter(CurseManifestLookupTask::needsResolution)
                .map(file -> Integer.toString(file.fileID()))
                .distinct()
                .toList();
        if (ids.isEmpty())
            return Map.of();

        try {
            return CurseForgeRemoteAddonRepository.MODS.getAddonFiles(ids);
        } catch (IOException | JsonParseException e) {
            LOG.warning("Unable to fetch CurseForge files in batch, falling back to per-entry lookups", e);
            return Map.of();
        }
    }

    /// Resolves one manifest entry, returning it unchanged when CurseForge cannot answer.
    ///
    /// @param file the manifest entry
    /// @return the entry enriched with the remote file name, url and hashes, or `file` itself
    private CurseManifestFile resolveFileName(CurseManifestFile file) {
        if (!needsResolution(file))
            return file;

        try {
            RemoteAddon.File remoteFile = CurseForgeRemoteAddonRepository.MODS.getAddonFile(
                    Integer.toString(file.projectID()), Integer.toString(file.fileID()));
            return applyRemote(file, remoteFile);
        } catch (FileNotFoundException fof) {
            LOG.warning("Could not query api.curseforge.com for deleted mods: " + file.projectID() + ", " + file.fileID(), fof);
            notFound.set(true);
            return file;
        } catch (IOException | JsonParseException e) {
            LOG.warning("Unable to fetch the file name projectID=" + file.projectID() + ", fileID=" + file.fileID(), e);
            allNameKnown.set(false);
            return file;
        }
    }

    /// Waits for one dispatched job, rethrowing whatever it failed with.
    ///
    /// @param future the job to wait for
    /// @param <R>    the job result type
    /// @return the job's result
    /// @throws Exception the failure the job reported, or `InterruptedException` if cancelled
    private static <R> R await(Future<R> future) throws Exception {
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception)
                throw exception;
            if (cause instanceof Error error)
                throw error;
            throw e;
        }
    }

    /// Resolves the class id of every manifest entry with as few requests as possible.
    ///
    /// A failure is not fatal: entries without a class id fall back to the per-entry lookup in
    /// `CurseCompletionTask#guessFilePath` that the completion task used for every entry before.
    ///
    /// @return the class id of every entry the server answered for, keyed by project id
    private Map<String, Integer> loadAddonClassIds(List<CurseManifestFile> manifestFiles) {
        try {
            return CurseForgeRemoteAddonRepository.MODS.getAddonClassIds(
                    manifestFiles.stream()
                            .map(file -> Integer.toString(file.projectID()))
                            .toList());
        } catch (IOException | JsonParseException e) {
            LOG.warning("Unable to fetch CurseForge class ids in batch, falling back to per-entry lookups", e);
            return Map.of();
        }
    }
}
