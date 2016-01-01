/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.download;

/**
 *
 * @author hyh
 */
public interface DownloadListener {

    void OnProgress(int progress, int max);
    boolean OnFailed();
    void OnFailedMoreThan5Times(String url);
    void OnDone();
}