/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.download;

import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import org.jackhuang.hellominecraftlauncher.apis.ApplicationManager;
import org.jackhuang.hellominecraftlauncher.apis.HMCLLog;

/**
 *
 * @author hyh
 */

// This class downloads a file from a URL.
public class HttpDownloader implements Runnable {
    
    // Max size of download buffer.
    private static final int MAX_BUFFER_SIZE = 2048;
    
    // These are the status names.
    public static final String STATUSES[] = {"Downloading",
    "Paused", "Complete", "Cancelled", "Error"};
    
    // These are the status codes.
    public static final int DOWNLOADING = 0;
    public static final int PAUSED = 1;
    public static final int COMPLETE = 2;
    public static final int CANCELLED = 3;
    public static final int ERROR = 4;
    
    private final URL url; // download URL
    private int size; // size of download in bytes
    private int downloaded; // number of bytes downloaded
    private final DownloadListener listener;
    private final String filePath;
    
    // Constructor for Download.
    public HttpDownloader(URL url, String filePath, DownloadListener l) {        
        this.url = url;
        size = -1;
        downloaded = 0;
        this.filePath = filePath;
        this.listener = l;
    }
    
    // Get this download's URL.
    public String getUrl() {
        return url.toString();
    }
    
    // Download file.
    @Override
    public void run() {
        RandomAccessFile file = null;
        InputStream stream = null;
        
        try {
            
            
            // Open connection to URL.
            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();
            
            connection.setConnectTimeout(5000);
            connection.setRequestProperty("User-Agent", ApplicationManager.getTitle());
	    
            // Connect to server.
            connection.connect();
            
            // Make sure response code is in the 200 range.
            if (connection.getResponseCode() / 100 != 2) {
                throw new Exception();
            }
            
            // Check for valid content length.
            int contentLength = connection.getContentLength();
            if (contentLength < 1) {
                throw new Exception();
            }
            
            // Set the size for this download if it hasn't been already set.
            if (size == -1) {
                size = contentLength;
            }
            
            // Open file and seek to the end of it.
            file = new RandomAccessFile(filePath, "rw");
            file.seek(downloaded);
            
            stream = connection.getInputStream();
            while (true) {
            // Size buffer according to how much of the file is left to download.
                byte buffer[] = new byte[MAX_BUFFER_SIZE];
                
                // Read from server into buffer.
                int read = stream.read(buffer);
                if (read == -1)
                    break;
                
                // Write buffer to file.
                file.write(buffer, 0, read);
                downloaded += read;
                
                if(listener != null)
                    listener.OnProgress(downloaded, size);
            }
            if(listener != null)
                listener.OnDone();
        } catch (Exception e) {
            HMCLLog.err("Failed to download file" + url + "!", e);
        } finally {
            // Close file.
            if (file != null) {
                try {
                    file.close();
                } catch (Exception e) {}
            }
            
            // Close connection to server.
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception e) {}
            }
        }
    }
    
    public static void download(String url, String file, DownloadListener dl) throws MalformedURLException {
	new HttpDownloader(new URL(url), file, dl).run();
    }
}
