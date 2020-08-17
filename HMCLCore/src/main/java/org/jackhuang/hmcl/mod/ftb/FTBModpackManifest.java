package org.jackhuang.hmcl.mod.ftb;

import java.util.List;

/**
 * Auto-generated: 2020-08-16 19:8:16
 *
 * @author www.jsons.cn
 */
public class FTBModpackManifest {

    public String synopsis;
    public String description;
    public List<Art> art;
    public List<Links> links;
    public List<Authors> authors;
    public List<Versions> versions;
    public int installs;
    public int plays;
    public boolean featured;
    public int refreshed;
    public String notification;
    public Rating rating;
    public String status;
    public int id;
    public String name;
    public String type;
    public int updated;
    public List<Tags> tags;

    public class Links {

        public int id;
        public String name;
        public String link;
        public String type;

    }

    public class Rating {

        public int id;
        public boolean configured;
        public boolean verified;
        public int age;
        public boolean gambling;
        public boolean frightening;
        public boolean alcoholdrugs;
        public boolean nuditysexual;
        public boolean sterotypeshate;
        public boolean languageviolence;

    }

    public class Specs {

        public int id;
        public int minimum;
        public int recommended;

    }

    public class Versions {

        public Specs specs;
        public int id;
        public String name;
        public String type;
        public int updated;

    }

    public class Tags {

        public int id;
        public String name;

    }

    public class Authors {

        public String website;
        public int id;
        public String name;
        public String type;
        public int updated;

    }

    public class Art {

        public int width;
        public int height;
        public boolean compressed;
        public String url;
        public String sha1;
        public int size;
        public int id;
        public String type;
        public int updated;

    }
}