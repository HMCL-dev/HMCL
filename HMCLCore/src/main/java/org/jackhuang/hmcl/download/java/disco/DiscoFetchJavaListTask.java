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

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.java.JavaPackageType;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.util.*;

/**
 * @author Glavo
 */
public final class DiscoFetchJavaListTask extends Task<TreeMap<Integer, DiscoJavaRemoteVersion>> {

    public static final String API_ROOT = System.getProperty("hmcl.discoapi.override", "https://api.foojay.io/disco/v3.0");

    private static String getOperatingSystemName(OperatingSystem os) {
        return os == OperatingSystem.OSX ? "macos" : os.getCheckedName();
    }

    private static String getArchitectureName(Architecture arch) {
        return arch.getCheckedName();
    }

    private final DiscoJavaDistribution distribution;
    private final Task<String> fetchPackagesTask;

    public DiscoFetchJavaListTask(DownloadProvider downloadProvider, DiscoJavaDistribution distribution, Platform platform, JavaPackageType packageType) {
        this.distribution = distribution;

        HashMap<String, String> params = new HashMap<>();
        params.put("distribution", distribution.getApiParameter());

        switch (packageType) {
            case JDK:
            case JDKFX:
                params.put("package", "jdk");
                break;
            case JRE:
            case JREFX:
                params.put("package", "jre");
                break;
            default:
                throw new IllegalArgumentException("Unsupported package type: " + packageType);
        }

        params.put("javafx_bundled", Boolean.toString(packageType == JavaPackageType.JDKFX || packageType == JavaPackageType.JREFX));
        params.put("operating_system", getOperatingSystemName(platform.getOperatingSystem()));
        params.put("architecture", getArchitectureName(platform.getArchitecture()));
        params.put("archive_type", platform.getOperatingSystem() == OperatingSystem.WINDOWS ? "zip" : "tar.gz");
        params.put("directly_downloadable", "true");

        this.fetchPackagesTask = new GetTask(downloadProvider.injectURLWithCandidates(NetworkUtils.withQuery(API_ROOT + "/packages", params)));
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return Collections.singleton(fetchPackagesTask);
    }

    @Override
    public void execute() throws Exception {
        String json = fetchPackagesTask.getResult();
        List<DiscoJavaRemoteVersion> result = JsonUtils.<Result<DiscoJavaRemoteVersion>>fromNonNullJson(json, new TypeToken<Result<DiscoJavaRemoteVersion>>() {
        }.getType()).result;

        TreeMap<Integer, DiscoJavaRemoteVersion> map = new TreeMap<>();

        for (DiscoJavaRemoteVersion version : result) {
            if (!distribution.getApiParameter().equals(version.getDistribution()))
                continue;

            int jdkVersion = version.getJdkVersion();
            DiscoJavaRemoteVersion oldVersion = map.get(jdkVersion);
            if (oldVersion == null || VersionNumber.compare(version.getDistributionVersion(), oldVersion.getDistributionVersion()) > 0) {
                map.put(jdkVersion, version);
            }
        }

        setResult(map);
    }

    private static final class Result<T> {
        final List<T> result;
        final String message;

        private Result(List<T> result, String message) {
            this.result = result;
            this.message = message;
        }
    }
}
