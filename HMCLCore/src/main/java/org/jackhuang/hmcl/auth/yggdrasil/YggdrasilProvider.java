// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.yggdrasil;

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.auth.AuthenticationException;

import java.util.UUID;

/// Supplies the endpoints used by [YggdrasilService] for authentication and profile operations.
///
/// @see <a href="http://wiki.vg">http://wiki.vg</a>
public interface YggdrasilProvider {

    /// Returns the endpoint for authenticating credentials.
    WebURL getAuthenticationURL() throws AuthenticationException;

    /// Returns the endpoint for refreshing an access token.
    WebURL getRefreshmentURL() throws AuthenticationException;

    /// Returns the endpoint for validating an access token.
    WebURL getValidationURL() throws AuthenticationException;

    /// Returns the endpoint for invalidating an access token.
    WebURL getInvalidationURL() throws AuthenticationException;

    /// URL to upload skin.
    ///
    /// Headers:
    ///     Authentication: Bearer &lt;access token&gt;
    ///
    /// Payload:
    ///     The payload for this API consists of multipart form data. There are two parts (order does not matter b/c of boundary):
    ///     model: Empty string for the default model and "slim" for the slim model
    ///     file: Raw image file data
    ///
    /// @see <a href="https://wiki.vg/Mojang_API#Upload_Skin">https://wiki.vg/Mojang_API#Upload_Skin</a>
    /// @return url to upload skin
    /// @throws AuthenticationException if url cannot be generated. e.g. some parameter or query is malformed.
    /// @throws UnsupportedOperationException if the Yggdrasil provider does not support third-party skin uploading.
    WebURL getSkinUploadURL(UUID uuid) throws AuthenticationException, UnsupportedOperationException;

    /// Returns the endpoint for reading the profile properties of the given player UUID.
    WebURL getProfilePropertiesURL(UUID uuid) throws AuthenticationException;

}
