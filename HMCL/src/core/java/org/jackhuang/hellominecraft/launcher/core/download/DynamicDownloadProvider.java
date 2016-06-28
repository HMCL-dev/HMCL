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

	private static final String PROVIDER_ADDR = "http://localhost/provider.php";

	private volatile static DynamicDownloadProvider instance;

	private String librariesAddr = null;
	private String assetsAddr = null;
	private String name = "MCHost";

	public void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}

	public String getLibrariesAddr() {
		return librariesAddr;
	}

	public void setLibrariesAddr(String librariesAddr) {
		this.librariesAddr = librariesAddr;
	}

	public String getAssetsAddr() {
		return assetsAddr;
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
	public String getParsedDownloadURL(String str) {
		if (StrUtils.isNotBlank(librariesAddr)) {
			str = str.replace("https://libraries.minecraft.net", librariesAddr);
		}
		return super.getParsedDownloadURL(str);
	}

	public void init() {
		new Thread() {
			@Override
			public void run() {
				try {
					String providerInfo = NetUtils.get(PROVIDER_ADDR);
					Map<String, String> addrInfo = null;
					addrInfo = C.GSON.fromJson(providerInfo, new TypeToken<Map<String, String>>(){}.getType());
					if (addrInfo != null) {
						if (addrInfo.containsKey("libraries")) {
							String librariesAddr = addrInfo.get("libraries");
							if (StrUtils.isNotBlank(librariesAddr)) {
								DynamicDownloadProvider.this.setLibrariesAddr(librariesAddr);
							}
						}
						if (addrInfo.containsKey("assets")) {
							String assetsAddr = addrInfo.get("assets");
							if (StrUtils.isNotBlank(assetsAddr)) {
								DynamicDownloadProvider.this.setAssetsAddr(assetsAddr);
							}
						}
						if (addrInfo.containsKey("name")) {
							String name = addrInfo.get("name");
							if (StrUtils.isNotBlank(name)) {
								DynamicDownloadProvider.this.setName(name);
							}
						}
					}
				} catch (IOException ex) {

				}
			}
		}.start();
	}
}
