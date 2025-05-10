/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.java.disco;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.java.JavaPackageType;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.util.*;

/**
 * @author Glavo
 */
public final class DiscoFetchJavaListTask extends Task<EnumMap<JavaPackageType, TreeMap<Integer, DiscoJavaRemoteVersion>>> {

    public static final String API_ROOT = System.getProperty("hmcl.discoapi.override", "https://api.foojay.io/disco/v3.0");

    private final DiscoJavaDistribution distribution;
    private final String archiveType;
    private final Task<String> fetchPackagesTask;

    public DiscoFetchJavaListTask(DownloadProvider downloadProvider, DiscoJavaDistribution distribution, Platform platform) {
        this.distribution = distribution;
        this.archiveType = platform.getOperatingSystem() == OperatingSystem.WINDOWS ? "zip" : "tar.gz";

        HashMap<String, String> params = new HashMap<>();
        params.put("distribution", distribution.getApiParameter());
        params.put("operating_system", platform.getOperatingSystem().getCheckedName());
        params.put("architecture", platform.getArchitecture().getCheckedName());
        params.put("archive_type", archiveType);
        params.put("directly_downloadable", "true");
        if (platform.getOperatingSystem() == OperatingSystem.LINUX)
            params.put("lib_c_type", "glibc");

        this.fetchPackagesTask = new GetTask(downloadProvider.injectURLWithCandidates(NetworkUtils.withQuery(API_ROOT + "/packages", params)));
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return Collections.singleton(fetchPackagesTask);
    }

    @Override
    public void execute() throws Exception {
        String json = fetchPackagesTask.getResult();
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

        setResult(result);
    }

}
