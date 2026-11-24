// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

/// Represents a page that provides search functionality with an expandable search bar.
public interface SearchablePage {

    /// Opens the search bar and requests focus for the search input field.
    void openSearch();

    /// Returns whether the search bar is currently visible/expanded.
    boolean isSearchBarVisible();

    /// Closes the search bar and clears the filter.
    void closeSearch();
}
