// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.java.disco;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.java.JavaPackageType;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.NativePlatform;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.util.*;

/**
 * @author Glavo
 */
public final class DiscoFetchJavaListTask extends Task<EnumMap<JavaPackageType, TreeMap<Integer, DiscoJavaRemoteVersion>>> {

    public static final String API_ROOT = System.getProperty("hmcl.discoapi.override", "https://api.foojay.io/disco/v3.0");

    /// Supplies candidate URLs for the package query.
    private final DownloadProvider downloadProvider;
    /// Distribution whose packages are requested.
    private final DiscoJavaDistribution distribution;
    /// Platform for which packages must be available.
    private final NativePlatform platform;

    /// Creates a package query for the selected distribution and platform.
    public DiscoFetchJavaListTask(DownloadProvider downloadProvider, DiscoJavaDistribution distribution, NativePlatform platform) {
        this.downloadProvider = Objects.requireNonNull(downloadProvider);
        this.distribution = Objects.requireNonNull(distribution);
        this.platform = Objects.requireNonNull(platform);
    }

    /// Returns the latest matching distribution version per JDK major version and package type.
    @Override
    public EnumMap<JavaPackageType, TreeMap<Integer, DiscoJavaRemoteVersion>> execute(TaskExecutionContext context) throws Exception {
        String archiveType = platform.os() == OperatingSystem.WINDOWS ? "zip" : "tar.gz";
        HashMap<String, String> params = new HashMap<>();
        params.put("distribution", distribution.getApiParameter());
        params.put("operating_system", platform.os().getCheckedName());
        params.put("architecture", platform.arch().getCheckedName());
        params.put("archive_type", archiveType);
        params.put("directly_downloadable", "true");
        if (platform.os() == OperatingSystem.LINUX)
            params.put("lib_c_type", "glibc");

        String url = NetworkUtils.withQuery(API_ROOT + "/packages", params);
        String json = context.run(new GetTask(downloadProvider.getDownloadCandidates(url)));
        List<DiscoJavaRemoteVersion> list = JsonUtils.fromNonNullJson(json, DiscoResult.typeOf(DiscoJavaRemoteVersion.class)).getResult();
        EnumMap<JavaPackageType, TreeMap<Integer, DiscoJavaRemoteVersion>> result = new EnumMap<>(JavaPackageType.class);

        for (DiscoJavaRemoteVersion version : list) {
            if (!distribution.getApiParameter().equals(version.getDistribution())
                    || !version.isDirectlyDownloadable()
                    || !archiveType.equals(version.getArchiveType()))
                continue;

            if (!distribution.testVersion(version))
                continue;

            JavaPackageType packageType = JavaPackageType.of("jdk".equals(version.getPackageType()), version.isJavaFXBundled());
            TreeMap<Integer, DiscoJavaRemoteVersion> map = result.computeIfAbsent(packageType, ignored -> new TreeMap<>());

            int jdkVersion = version.getJdkVersion();
            DiscoJavaRemoteVersion oldVersion = map.get(jdkVersion);
            if (oldVersion == null || VersionNumber.compare(version.getDistributionVersion(), oldVersion.getDistributionVersion()) > 0)
                map.put(jdkVersion, version);
        }

        return result;
    }
}
