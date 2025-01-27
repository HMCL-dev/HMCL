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
import org.jackhuang.hmcl.util.gson.JsonUtils;

/**
 * @author Glavo
 */
public final class DiscoJavaRemoteVersion implements JavaRemoteVersion {
    @SerializedName("id")
    private final String id;

    @SerializedName("archive_type")
    private final String archiveType;

    @SerializedName("distribution")
    private final String distribution;

    @SerializedName("major_version")
    private final int majorVersion;

    @SerializedName("java_version")
    private final String javaVersion;

    @SerializedName("distribution_version")
    private final String distributionVersion;

    @SerializedName("jdk_version")
    private final int jdkVersion;

    @SerializedName("latest_build_available")
    private final boolean latestBuildAvailable;

    @SerializedName("release_status")
    private final String releaseStatus;

    @SerializedName("term_of_support")
    private final String termOfSupport;

    @SerializedName("operating_system")
    private final String operatingSystem;

    @SerializedName("lib_c_type")
    private final String libCType;

    @SerializedName("architecture")
    private final String architecture;

    @SerializedName("fpu")
    private final String fpu;

    @SerializedName("package_type")
    private final String packageType;

    @SerializedName("javafx_bundled")
    private final boolean javafxBundled;

    @SerializedName("directly_downloadable")
    private final boolean directlyDownloadable;

    @SerializedName("filename")
    private final String fileName;

    @SerializedName("links")
    private final Links links;

    @SerializedName("free_use_in_production")
    private final boolean freeUseInProduction;

    @SerializedName("tck_tested")
    private final String tckTested;

    @SerializedName("tck_cert_uri")
    private final String tckCertUri;

    @SerializedName("aqavit_certified")
    private final String aqavitCertified;

    @SerializedName("aqavit_cert_uri")
    private final String aqavitCertUri;

    @SerializedName("size")
    private final long size;

    public DiscoJavaRemoteVersion(String id, String archiveType, String distribution, int majorVersion, String javaVersion, String distributionVersion, int jdkVersion, boolean latestBuildAvailable, String releaseStatus, String termOfSupport, String operatingSystem, String libCType, String architecture, String fpu, String packageType, boolean javafxBundled, boolean directlyDownloadable, String fileName, Links links, boolean freeUseInProduction, String tckTested, String tckCertUri, String aqavitCertified, String aqavitCertUri, long size) {
        this.id = id;
        this.archiveType = archiveType;
        this.distribution = distribution;
        this.majorVersion = majorVersion;
        this.javaVersion = javaVersion;
        this.distributionVersion = distributionVersion;
        this.jdkVersion = jdkVersion;
        this.latestBuildAvailable = latestBuildAvailable;
        this.releaseStatus = releaseStatus;
        this.termOfSupport = termOfSupport;
        this.operatingSystem = operatingSystem;
        this.libCType = libCType;
        this.architecture = architecture;
        this.fpu = fpu;
        this.packageType = packageType;
        this.javafxBundled = javafxBundled;
        this.directlyDownloadable = directlyDownloadable;
        this.fileName = fileName;
        this.links = links;
        this.freeUseInProduction = freeUseInProduction;
        this.tckTested = tckTested;
        this.tckCertUri = tckCertUri;
        this.aqavitCertified = aqavitCertified;
        this.aqavitCertUri = aqavitCertUri;
        this.size = size;
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

    public int getMajorVersion() {
        return majorVersion;
    }

    @Override
    public String getJavaVersion() {
        return javaVersion;
    }

    @Override
    public String getDistributionVersion() {
        return distributionVersion;
    }

    @Override
    public int getJdkVersion() {
        return jdkVersion;
    }

    public boolean isLatestBuildAvailable() {
        return latestBuildAvailable;
    }

    public String getReleaseStatus() {
        return releaseStatus;
    }

    public String getTermOfSupport() {
        return termOfSupport;
    }

    public boolean isLTS() {
        return "lts".equals(termOfSupport);
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public String getLibCType() {
        return libCType;
    }

    public String getArchitecture() {
        return architecture;
    }

    public String getFpu() {
        return fpu;
    }

    public String getPackageType() {
        return packageType;
    }

    public boolean isJavaFXBundled() {
        return javafxBundled;
    }

    public boolean isDirectlyDownloadable() {
        return directlyDownloadable;
    }

    public String getFileName() {
        return fileName;
    }

    public Links getLinks() {
        return links;
    }

    public boolean isFreeUseInProduction() {
        return freeUseInProduction;
    }

    public String getTckTested() {
        return tckTested;
    }

    public String getTckCertUri() {
        return tckCertUri;
    }

    public String getAqavitCertified() {
        return aqavitCertified;
    }

    public String getAqavitCertUri() {
        return aqavitCertUri;
    }

    public long getSize() {
        return size;
    }

    @Override
    public String toString() {
        return "DiscoJavaRemoteVersion " + JsonUtils.GSON.toJson(this);
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
