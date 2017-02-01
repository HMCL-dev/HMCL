/*
 * Hello Minecraft!.
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
package org.jackhuang.hellominecraft.util;

import java.util.Map;

/**
 *
 * @author huangyuhui
 */
public interface IUpdateChecker {

    /**
     *
     */
    void checkOutdate();

    /**
     * Get the <b>cached</b> newest version number, use "process" method to
     * download!
     *
     * @return the newest version number
     *
     * @see process
     */
    VersionNumber getNewVersion();

    /**
     * Download the version number synchronously. When you execute this method
     * first, should leave "showMessage" false.
     *
     * @param showMessage If it is requested to warn the user that there is a
     *                    new version.
     *
     * @return the process observable.
     */
    AbstractSwingWorker<VersionNumber> process(boolean showMessage);

    /**
     * Get the download links.
     *
     * @return a JSON, which contains the server response.
     */
    AbstractSwingWorker<Map<String, String>> requestDownloadLink();

}
