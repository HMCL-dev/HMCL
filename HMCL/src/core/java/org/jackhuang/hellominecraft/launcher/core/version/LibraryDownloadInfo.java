/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hellominecraft.launcher.core.version;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hellominecraft.launcher.core.download.DownloadType;
import org.jackhuang.hellominecraft.launcher.core.download.IDownloadProvider;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.system.IOUtils;

/**
 *
 * @author huangyuhui
 */
public class LibraryDownloadInfo extends GameDownloadInfo {

    @SerializedName("path")
    public String path;
    @SerializedName("forgeURL")
    public String forgeURL;

    @Override
    public String getUrl(DownloadType dt, boolean allowSelf) {
		IDownloadProvider provider = dt.getProvider();
        String downloadUrl = (forgeURL == null ? provider.getLibraryDownloadURL() : forgeURL);
        if (StrUtils.isNotBlank(url) && allowSelf) {
            downloadUrl = url;
		} // forced replace: Let different download sources into force
        return provider.getParsedDownloadURL(getUrlWithBaseUrl(downloadUrl));
    }
	
	public String getRetryUrl(DownloadType dt) {
		IDownloadProvider provider = dt.getProvider();
		String retryBaseUrl = provider.getRetryLibraryDownloadURL();
		
        String downloadUrl = (forgeURL == null ? retryBaseUrl : forgeURL);
        if (StrUtils.isNotBlank(url) && provider.isAllowedToUseSelfURL()) {
            downloadUrl = url;
		}
		
		if (StrUtils.isBlank(downloadUrl)) {
			return null;
		}
		
		downloadUrl = getUrlWithBaseUrl(downloadUrl);
		if (downloadUrl.contains("minecraftforge")) { // fix forge url
			downloadUrl = provider.getParsedDownloadURL(downloadUrl);
		}
		
		return downloadUrl;
	}
	
	private String getUrlWithBaseUrl(String baseUrl) {
		if (!baseUrl.endsWith(".jar")) {
            if (path == null) {
                return null;
			} else {
                baseUrl = IOUtils.addURLSeparator(baseUrl) + path.replace('\\', '/');
			}
		}
		return baseUrl;
	}
}
