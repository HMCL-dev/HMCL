/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.upgrade

import java.io.IOException
import com.google.gson.JsonSyntaxException
import org.jackhuang.hmcl.task.TaskResult
import org.jackhuang.hmcl.util.*
import org.jackhuang.hmcl.util.Constants.GSON
import org.jackhuang.hmcl.util.Logging.LOG
import java.util.logging.Level


/**
 *
 * @author huangyuhui
 */
class UpdateChecker(var base: VersionNumber, var type: String) {

    @Volatile
    var isOutOfDate = false
        private set
    var versionString: String? = null
    private var download_link: Map<String, String>? = null

    /**
     * Get the <b>cached</b> newest version number, use "process" method to
     * download!
     *
     * @return the newest version number
     *
     * @see process
     */
    var newVersion: VersionNumber? = null
        internal set

    /**
     * Download the version number synchronously. When you execute this method
     * first, should leave "showMessage" false.
     *
     * @param showMessage If it is requested to warn the user that there is a
     *                    new version.
     *
     * @return the process observable.
     */
    fun process(showMessage: Boolean): TaskResult<VersionNumber> {
        return object : TaskResult<VersionNumber>() {
            override fun getId() = "update_checker.process"
            override fun execute() {
                if (newVersion == null) {
                    versionString = NetworkUtils.doGet("http://huangyuhui.duapp.com/info.php?type=$type".toURL())
                    newVersion = VersionNumber.asVersion(versionString!!)
                }

                if (newVersion == null) {
                    LOG.warning("Failed to check update...")
                } else if (base < newVersion!!)
                    isOutOfDate = true
                if (isOutOfDate)
                    result = newVersion
            }
        }
    }

    /**
     * Get the download links.
     *
     * @return a JSON, which contains the server response.
     */
    @Synchronized
    fun requestDownloadLink(): TaskResult<Map<String, String>> {
        return object : TaskResult<Map<String, String>>() {
            override fun getId() = "update_checker.request_download_link"
            override fun execute() {
                @Suppress("UNCHECKED_CAST")
                if (download_link == null)
                    try {
                        download_link = GSON.fromJson(NetworkUtils.doGet("http://huangyuhui.duapp.com/update_link.php?type=$type".toURL()), Map::class.java) as Map<String, String>
                    } catch (e: JsonSyntaxException) {
                        LOG.log(Level.WARNING, "Failed to get update link.", e)
                    } catch (e: IOException) {
                        LOG.log(Level.WARNING, "Failed to get update link.", e)
                    }

                result = download_link
            }
        }
    }
/*
    val upgrade: EventHandler<SimpleEvent<VersionNumber>> = EventHandler()

    fun checkOutdate() {
        if (isOutOfDate)
            if (EVENT_BUS.fireChannelResulted(OutOfDateEvent(this, newVersion)))
                upgrade.fire(SimpleEvent(this, newVersion))
    }*/
}
