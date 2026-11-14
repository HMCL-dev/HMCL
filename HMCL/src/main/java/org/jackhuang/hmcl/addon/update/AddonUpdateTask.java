// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.update;

import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.LocalAddonManager;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class AddonUpdateTask extends Task<Void> {
    private final Collection<Task<?>> dependents = new ArrayList<>();
    private final List<LocalAddonFile<?>> failedAddons = new ArrayList<>();

    public AddonUpdateTask(DownloadProvider downloadProvider, List<AddonUpdate> addons) {
        setStage("addon.check_update.confirm");
        getProperties().put("total", addons.size());

        for (AddonUpdate addon : addons) {
            LocalAddonFile<?> local = addon.localAddonFile();
            RemoteAddon.Version remote = addon.targetVersion();
            boolean isDisabled = local.isDisabled();
            String fileName = remote.file().filename();
            if (isDisabled)
                fileName = StringUtils.addSuffix(fileName, LocalAddonManager.DISABLED_EXTENSION);
            String newFileName = fileName;
            Path targetFile = addon.localAddonFile().getFile().resolveSibling(newFileName);

            dependents.add(Task
                    .supplyAsync(Schedulers.io(), () -> Files.createTempFile("hmcl-addon-update-", ".tmp"))
                    .thenComposeAsync(tempFile ->
                            new FileDownloadTask(downloadProvider.getDownloadCandidates(remote.file().url()), tempFile)
                                    .setName(remote.name())
                                    .thenRunAsync(Schedulers.javafx(), () -> local.setOld(true))
                                    .thenRunAsync(Schedulers.io(), () -> Files.move(tempFile, targetFile))
                                    .whenComplete(Schedulers.javafx(), exception -> {
                                        try {
                                            Files.deleteIfExists(tempFile);
                                        } catch (Exception e) {
                                            LOG.warning("Failed to delete temp file " + tempFile, e);
                                        }
                                        if (exception != null) {
                                            // restore state if failed
                                            local.setOld(false);
                                            if (isDisabled)
                                                local.markDisabled();
                                        } else {
                                            local.onUpdated(newFileName);
                                            if (!local.keepOldFiles()) {
                                                try {
                                                    local.delete();
                                                } catch (IOException e) {
                                                    LOG.warning("Failed to delete outdated addon: " + local.getFile(), e);
                                                }
                                            }
                                        }
                                    })
                    ).whenComplete(Schedulers.javafx(), exception -> {
                        if (exception != null) {
                            LOG.warning("Failed to update addon", exception);
                            failedAddons.add(local);
                        }
                    }).withCounter("addon.check_update.confirm"));
        }
    }

    public List<LocalAddonFile<?>> getFailedAddons() {
        return failedAddons;
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return dependents;
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() {
        notifyPropertiesChanged();
    }

    @Override
    public boolean isRelyingOnDependents() {
        return false;
    }

    @Override
    public void execute() throws Exception {
        if (!isDependentsSucceeded())
            throw getException();
    }
}
