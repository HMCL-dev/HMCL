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

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.download.java.JavaRemoteVersion;

/**
 * @author Glavo
 */
public final class DiscoRemoteVersion implements JavaRemoteVersion {
    @SerializedName("id")
    private final String id;

    @SerializedName("archive_type")
    private final String archiveType;

    @SerializedName("distribution")
    private final String distribution;

    @SerializedName("jdk_version")
    private final int jdkVersion;

    @SerializedName("java_version")
    private final String javaVersion;

    @SerializedName("distribution_version")
    private final String distributionVersion;

    @SerializedName("operating_system")
    private final String operatingSystem;

    @SerializedName("architecture")
    private final String architecture;

    @SerializedName("package_type")
    private final String packageType;

    @SerializedName("directly_downloadable")
    private final boolean directlyDownloadable;

    @SerializedName("links")
    private final Links links;

    public DiscoRemoteVersion(String id, String archiveType, String distribution, int jdkVersion, String javaVersion, String distributionVersion, String operatingSystem, String architecture, String packageType, boolean directlyDownloadable, Links links) {
        this.id = id;
        this.archiveType = archiveType;
        this.distribution = distribution;
        this.jdkVersion = jdkVersion;
        this.javaVersion = javaVersion;
        this.distributionVersion = distributionVersion;
        this.operatingSystem = operatingSystem;
        this.architecture = architecture;
        this.packageType = packageType;
        this.directlyDownloadable = directlyDownloadable;
        this.links = links;
    }

    public String getId() {
        return id;
    }

    public String getArchiveType() {
        return archiveType;
    }

    public String getDistribution() {
        return distribution;
    }

    @Override
    public int getJdkVersion() {
        return jdkVersion;
    }

    @Override
    public String getJavaVersion() {
        return javaVersion;
    }

    public String getDistributionVersion() {
        return distributionVersion;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public String getArchitecture() {
        return architecture;
    }

    public String getPackageType() {
        return packageType;
    }

    public boolean isDirectlyDownloadable() {
        return directlyDownloadable;
    }

    public Links getLinks() {
        return links;
    }

    public static final class Links {
        @SerializedName("pkg_info_uri")
        private final String pkgInfoUri;

        @SerializedName("pkg_download_redirect")
        private final String pkgDownloadRedirect;

        public Links(String pkgInfoUri, String pkgDownloadRedirect) {
            this.pkgInfoUri = pkgInfoUri;
            this.pkgDownloadRedirect = pkgDownloadRedirect;
        }

        public String getPkgInfoUri() {
            return pkgInfoUri;
        }

        public String getPkgDownloadRedirect() {
            return pkgDownloadRedirect;
        }
    }
}
