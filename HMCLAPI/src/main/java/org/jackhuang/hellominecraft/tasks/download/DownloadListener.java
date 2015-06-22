/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.tasks.download;

import org.jackhuang.hellominecraft.tasks.ProgressProviderListener;

/**
 *
 * @author hyh
 */
public interface DownloadListener extends ProgressProviderListener {

    boolean OnFailed();
    void OnFailedMoreThan5Times(String url);
}