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
package org.jackhuang.hellominecraft.launcher.core.download;

import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.util.Map;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.NetUtils;
import org.jackhuang.hellominecraft.util.StrUtils;

/**
 *
 * @author evilwk <evilwk@gmail.com>
 */
public class DynamicDownloadProvider extends MojangDownloadProvider {

	private static final String PROVIDER_ADDR = "http://client.api.mcgogogo.com:81/provider.php";
	
	private volatile static DynamicDownloadProvider instance;

	private String versionManifestAddr = null;
	private String launcherMetaAddr = null;
	private String launcherAddr = null;
	
	private String librariesAddr = null;
	private String assetsAddr = null;
	
	public void setVersionManifestAddr(String versionManifestAddr) {
		this.versionManifestAddr = versionManifestAddr;
	}

	public void setLauncherMetaAddr(String launcherMetaAddr) {
		this.launcherMetaAddr = launcherMetaAddr;
	}

	public void setLauncherAddr(String launcherAddr) {
		this.launcherAddr = launcherAddr;
	}

	public void setLibrariesAddr(String librariesAddr) {
		this.librariesAddr = librariesAddr;
	}

	public void setAssetsAddr(String assetsAddr) {
		this.assetsAddr = assetsAddr;
	}

	private DynamicDownloadProvider() {

	}

	public static DynamicDownloadProvider getInstance() {
		if (instance == null) {
			synchronized (DynamicDownloadProvider.class) {
				if (instance == null) {
					instance = new DynamicDownloadProvider();
				}
			}
		}
		return instance;
	}

	@Override
	public String getRetryAssetsDownloadURL() {
		return super.getAssetsDownloadURL();
	}

	@Override
	public String getRetryLibraryDownloadURL() {
		return super.getLibraryDownloadURL();
	}

	@Override
	public String getAssetsDownloadURL() {
		if (StrUtils.isNotBlank(assetsAddr)) {
			return assetsAddr;
		}
		return super.getAssetsDownloadURL();
	}

	@Override
	public String getLibraryDownloadURL() {
		if (StrUtils.isNotBlank(librariesAddr)) {
			return librariesAddr;
		}
		return super.getLibraryDownloadURL();
	}

	@Override
	public String getVersionsListDownloadURL() {
		if (StrUtils.isNotBlank(versionManifestAddr)) {
			return versionManifestAddr;
		}
		return super.getVersionsListDownloadURL();
	}
	
	@Override
	public String getParsedDownloadURL(String str) {
		if (str != null) {	
			if (StrUtils.isNotBlank(librariesAddr)) {
				str = str.replace("https://libraries.minecraft.net", librariesAddr);
			}
			if (StrUtils.isNotBlank(launcherMetaAddr)) {
				str = str.replace("https://launchermeta.mojang.com", launcherMetaAddr);
			}
			if (StrUtils.isNotBlank(launcherAddr)) {
				str = str.replace("https://launcher.mojang.com", launcherAddr);
			}
		}
		return super.getParsedDownloadURL(str);
	}

	public void init() {
		new Thread() {
			
			private String getValue(Map<String, String> addrInfo, String key) {
				String value = null;
				do {
					if (!addrInfo.containsKey(key))
						continue;
					value = addrInfo.get(key);
				} while(false);
				return value;
			}
			
			@Override
			public void run() {
				try {
					String providerInfo = NetUtils.get(PROVIDER_ADDR);
					Map<String, String> addrInfo = null;
					addrInfo = C.GSON.fromJson(providerInfo, new TypeToken<Map<String, String>>() {
					}.getType());
					if (addrInfo != null) {
						setLibrariesAddr(getValue(addrInfo, "libraries"));
						setAssetsAddr(getValue(addrInfo, "assets"));
						setLauncherMetaAddr(getValue(addrInfo, "launcherMeta"));
						setLauncherAddr(getValue(addrInfo, "launcher"));
						setVersionManifestAddr(getValue(addrInfo, "versionManifest"));
					}
				} catch (IOException ex) {

				}
			}
		}.start();
	}
}
