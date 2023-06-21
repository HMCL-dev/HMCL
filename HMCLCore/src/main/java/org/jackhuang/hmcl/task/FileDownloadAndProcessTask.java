package org.jackhuang.hmcl.task;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public class FileDownloadAndProcessTask extends FileDownloadTask {
    @FunctionalInterface
    public interface FileProcessor {
        void process(File file) throws IOException;
    }

    private final FileProcessor processor;

    public FileDownloadAndProcessTask(URL url, File file, FileProcessor processer) {
        super(url, file);
        this.processor = processer;
    }

    public FileDownloadAndProcessTask(URL url, File file, IntegrityCheck integrityCheck, FileProcessor processer) {
        super(url, file, integrityCheck);
        this.processor = processer;
    }

    public FileDownloadAndProcessTask(URL url, File file, IntegrityCheck integrityCheck, int retry, FileProcessor processer) {
        super(url, file, integrityCheck, retry);
        this.processor = processer;
    }

    public FileDownloadAndProcessTask(List<URL> urls, File file, FileProcessor processer) {
        super(urls, file);
        this.processor = processer;
    }

    public FileDownloadAndProcessTask(List<URL> urls, File file, IntegrityCheck integrityCheck, FileProcessor processer) {
        super(urls, file, integrityCheck);
        this.processor = processer;
    }

    public FileDownloadAndProcessTask(List<URL> urls, File file, IntegrityCheck integrityCheck, int retry, FileProcessor processer) {
        super(urls, file, integrityCheck, retry);
        this.processor = processer;
    }

    @Override
    public void execute() throws Exception {
        super.execute();
        this.processor.process(this.getFile());
    }
}
