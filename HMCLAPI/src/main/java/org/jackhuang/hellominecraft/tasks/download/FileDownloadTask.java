/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.tasks.download;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.tasks.Task;
import org.jackhuang.hellominecraft.tasks.communication.PreviousResult;
import org.jackhuang.hellominecraft.tasks.communication.PreviousResultRegistrator;
import org.jackhuang.hellominecraft.utils.IOUtils;

/**
 *
 * @author hyh
 */
// This class downloads a file from a URL.
public class FileDownloadTask extends Task implements PreviousResult<File>, PreviousResultRegistrator<String> {

    private static final X509TrustManager xtm = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    };
    private static final HostnameVerifier hnv = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    static {
        SSLContext sslContext = null;

        try {
            sslContext = SSLContext.getInstance("TLS");
            X509TrustManager[] xtmArray = new X509TrustManager[]{xtm};
            sslContext.init(null, xtmArray, new java.security.SecureRandom());
        } catch (GeneralSecurityException gse) {
        }
        if (sslContext != null) {
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        }

        HttpsURLConnection.setDefaultHostnameVerifier(hnv);
    }

    // Max size of download buffer.
    private static final int MAX_BUFFER_SIZE = 2048;

    private URL url; // download URL
    private int size; // size of download in bytes
    private int downloaded; // number of bytes downloaded
    private final File filePath;

    public FileDownloadTask(File filePath) {
        this((URL) null, filePath);
    }

    public FileDownloadTask(String url, File filePath) {
        this(IOUtils.parseURL(url), filePath);
    }

    // Constructor for Download.
    public FileDownloadTask(URL url, File filePath) {
        this.url = url;
        size = -1;
        downloaded = 0;
        this.filePath = filePath;
    }

    // Get this download's URL.
    public String getUrl() {
        return url.toString();
    }

    RandomAccessFile file = null;
    InputStream stream = null;
    boolean shouldContinue = true, aborted = false;

    private void closeFiles() {
        // Close file.
        if (file != null) {
            try {
                file.close();
                file = null;
            } catch (IOException e) {
                HMCLog.warn("Failed to close file", e);
            }
        }

        // Close connection to server.
        if (stream != null) {
            try {
                stream.close();
                stream = null;
            } catch (IOException e) {
                HMCLog.warn("Failed to close stream", e);
            }
        }
    }

    // Download file.
    @Override
    public boolean executeTask() {
        for (PreviousResult<String> p : al) {
            this.url = IOUtils.parseURL(p.getResult());
        }

        for (int repeat = 0; repeat < 6; repeat++) {
            if (repeat > 0) {
                HMCLog.warn("Failed to download, repeat: " + repeat);
            }
            try {

                // Open connection to URL.
                HttpURLConnection connection
                        = (HttpURLConnection) url.openConnection();

                connection.setConnectTimeout(5000);
                connection.setRequestProperty("User-Agent", "Hello Minecraft! Launcher");

                // Connect to server.
                connection.connect();

                // Make sure response code is in the 200 range.
                if (connection.getResponseCode() / 100 != 2) {
                    setFailReason(new NetException(C.i18n("download.not_200") + " " + connection.getResponseCode()));
                    return false;
                }

                // Check for valid content length.
                int contentLength = connection.getContentLength();
                if (contentLength < 1) {
                    setFailReason(new NetException("The content length is invalid."));
                    return false;
                }

                // Set the size for this download if it hasn't been already set.
                if (size == -1) {
                    size = contentLength;
                }

                filePath.getParentFile().mkdirs();

                File tempFile = new File(filePath.getAbsolutePath() + ".hmd");
                if (!tempFile.exists()) {
                    tempFile.createNewFile();
                }

                // Open file and seek to the end of it.
                file = new RandomAccessFile(tempFile, "rw");
                file.seek(downloaded);

                stream = connection.getInputStream();
                while (true) {
                    // Size buffer according to how much of the file is left to download.
                    if (!shouldContinue) {
                        closeFiles();
                        filePath.delete();
                        break;
                    }

                    byte buffer[] = new byte[MAX_BUFFER_SIZE];

                    // Read from server into buffer.
                    int read = stream.read(buffer);
                    if (read == -1) {
                        break;
                    }

                    // Write buffer to file.
                    file.write(buffer, 0, read);
                    downloaded += read;

                    if (ppl != null) {
                        ppl.setProgress(downloaded, size);
                    }
                }
                closeFiles();
                tempFile.renameTo(filePath);
                if (ppl != null) {
                    ppl.onProgressProviderDone();
                }
                return true;
            } catch (Exception e) {
                setFailReason(new NetException(C.i18n("download.failed") + " " + url, e));
            } finally {
                closeFiles();
            }
        }
        return false;
    }

    public static void download(String url, String file, DownloadListener dl) {
        ((Task) new FileDownloadTask(url, new File(file)).setProgressProviderListener(dl)).executeTask();
    }

    @Override
    public boolean abort() {
        shouldContinue = false;
        aborted = true;
        return true;
    }

    @Override
    public String getInfo() {
        return C.i18n("download") + ": " + url + " " + filePath;
    }

    @Override
    public File getResult() {
        return filePath;
    }

    ArrayList<PreviousResult<String>> al = new ArrayList();

    @Override
    public Task registerPreviousResult(PreviousResult<String> pr) {
        al.add(pr);
        return this;
    }
}
