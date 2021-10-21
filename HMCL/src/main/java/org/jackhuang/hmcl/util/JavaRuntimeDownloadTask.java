package org.jackhuang.hmcl.util;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.task.Task;

import java.util.Collection;

public class JavaRuntimeDownloadTask extends Task<Void> {

    @Override
    public void execute() {

//        HttpRequest.GET("https://hmcl.huangyuhui.net/api/java",
//                pair("os", OperatingSystem.CURRENT_OS.getCheckedName()));
//        .getJson();


    }

    @Override
    public Collection<? extends Task<?>> getDependencies() {
        return super.getDependencies();
    }

    public static class JavaDownload {
        @SerializedName("version")
        private final String version;

        @SerializedName("distro")
        private final String distro;

        @SerializedName("url")
        private final String url;

        public JavaDownload() {
            this("", "", "");
        }

        public JavaDownload(String version, String distro, String url) {
            this.version = version;
            this.distro = distro;
            this.url = url;
        }

        public String getVersion() {
            return version;
        }

        public String getDistro() {
            return distro;
        }

        public String getUrl() {
            return url;
        }
    }
}
