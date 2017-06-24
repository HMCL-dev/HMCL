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
package org.jackhuang.hmcl.util;

import java.io.File;
import org.jackhuang.hmcl.core.asset.MinecraftAssetService;
import org.jackhuang.hmcl.core.service.IMinecraftService;
import org.jackhuang.hmcl.setting.Settings;

/**
 *
 * @author huang
 */
public class HMCLAssetService extends MinecraftAssetService {
    
    public HMCLAssetService(IMinecraftService service) {
        super(service);
    }
    
    private boolean useSelf(String assetId) {
        return new File(service.baseDirectory(), "assets/indexes/" + assetId + ".json").exists() || ((HMCLMinecraftService) service).getProfile().isNoCommon();
    }

    @Override
    public File getAssets(String assetId) {
        if (useSelf(assetId))
            return new File(service.baseDirectory(), "assets");
        else
            return new File(Settings.getInstance().getCommonpath(), "assets");
    }
    
}
