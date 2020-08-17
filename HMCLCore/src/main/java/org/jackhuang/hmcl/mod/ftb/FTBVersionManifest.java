package org.jackhuang.hmcl.mod.ftb;

import java.util.List;

/**
 * Auto-generated: 2020-08-16 17:53:32
 *
 * @author www.jsons.cn
 */
public class FTBVersionManifest {

    public List<Files> files;
    public Specs specs;
    public List<Targets> targets;
    public int installs;
    public int plays;
    public int refreshed;
    public String changelog;
    public int parent;
    public String notification;
    public List<String> links;
    public String status;
    public int id;
    public String name;
    public String type;
    public int updated;

    public static class Specs {

        public int id;
        public int minimum;
        public int recommended;

    }

    public static class Targets {

        public String version;
        public int id;
        public String name;
        public String type;
        public int updated;

    }

    public static class Files {

        public String version;
        public String path;
        public String url;
        public String sha1;
        public int size;
        public List<String> tags;
        public boolean clientonly;
        public boolean serveronly;
        public boolean optional;
        public int id;
        public String name;
        public String type;
        public int upStringd;

    }

}