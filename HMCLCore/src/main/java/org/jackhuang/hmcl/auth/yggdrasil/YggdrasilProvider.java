/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.auth.yggdrasil;

import org.jackhuang.hmcl.auth.AuthenticationException;

import java.net.URL;
import java.util.UUID;

/**
 * @see <a href="http://wiki.vg">http://wiki.vg</a>
 */
public interface YggdrasilProvider {

    URL getAuthenticationURL() throws AuthenticationException;

    URL getRefreshmentURL() throws AuthenticationException;

    URL getValidationURL() throws AuthenticationException;

    URL getInvalidationURL() throws AuthenticationException;

    /**
     * URL to upload skin.
     *
     * Headers:
     *     Authentication: Bearer &lt;access token&gt;
     *
     * Payload:
     *     The payload for this API consists of multipart form data. There are two parts (order does not matter b/c of boundary):
     *     model: Empty string for the default model and "slim" for the slim model
     *     file: Raw image file data
     *
     * @see <a href="https://wiki.vg/Mojang_API#Upload_Skin">https://wiki.vg/Mojang_API#Upload_Skin</a>
     * @return url to upload skin
     * @throws AuthenticationException if url cannot be generated. e.g. some parameter or query is malformed.
     * @throws UnsupportedOperationException if the Yggdrasil provider does not support third-party skin uploading.
     */
    URL getSkinUploadURL(UUID uuid) throws AuthenticationException, UnsupportedOperationException;

    URL getProfilePropertiesURL(UUID uuid) throws AuthenticationException;

}
