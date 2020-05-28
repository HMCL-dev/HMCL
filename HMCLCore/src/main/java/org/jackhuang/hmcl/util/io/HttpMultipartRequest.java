package org.jackhuang.hmcl.util.io;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;

import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpMultipartRequest implements Closeable {
    private final String boundary = "*****" + System.currentTimeMillis() + "*****";
    private final HttpURLConnection urlConnection;
    private final ByteArrayOutputStream stream;
    private final String endl = "\r\n";

    public HttpMultipartRequest(HttpURLConnection urlConnection) throws IOException {
        this.urlConnection = urlConnection;
        urlConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        stream = new ByteArrayOutputStream();
    }

    private void addLine(String content) throws IOException {
        stream.write(content.getBytes(UTF_8));
        stream.write(endl.getBytes(UTF_8));
    }

    public HttpMultipartRequest file(String name, String filename, String contentType, InputStream inputStream) throws IOException {
        addLine("--" + boundary);
        addLine(String.format("Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"", name, filename));
        addLine("Content-Type: " + contentType);
        addLine("Content-Transfer-Encoding: binary");
        addLine("");
        IOUtils.copyTo(inputStream, stream);
        return this;
    }

    public HttpMultipartRequest param(String name, String value) throws IOException {
        addLine("--" + boundary);
        addLine(String.format("Content-Disposition: form-data; name=\"%s\"", name));
        addLine("");
        addLine(value);
        return this;
    }

    @Override
    public void close() throws IOException {
        addLine("--" + boundary + "--");
        urlConnection.setRequestProperty("Content-Length", "" + stream.size());
        try (OutputStream os = urlConnection.getOutputStream()) {
            IOUtils.write(stream.toByteArray(), os);
        }
    }
}
