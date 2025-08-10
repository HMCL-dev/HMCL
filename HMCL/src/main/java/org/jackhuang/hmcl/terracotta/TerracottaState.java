package org.jackhuang.hmcl.terracotta;

import javafx.beans.property.ReadOnlyDoubleProperty;

public abstract class TerracottaState {
    protected TerracottaState() {
    }

    public static final class Bootstrap extends TerracottaState {
        static final Bootstrap INSTANCE = new Bootstrap();

        private Bootstrap() {
        }
    }

    public static final class Uninitialized extends TerracottaState {
        static final Uninitialized INSTANCE = new Uninitialized();

        private Uninitialized() {
        }
    }

    public static final class Preparing extends TerracottaState {
        final ReadOnlyDoubleProperty progress;

        Preparing(ReadOnlyDoubleProperty progress) {
            this.progress = progress;
        }

        public ReadOnlyDoubleProperty progressProperty() {
            return progress;
        }
    }

    public static final class Launching extends TerracottaState {
        Launching() {
        }
    }

    static abstract class PortSpecific extends TerracottaState {
        final int port;

        protected PortSpecific(int port) {
            this.port = port;
        }
    }

    static abstract class Ready extends PortSpecific {
        final int index;

        Ready(int port, int index) {
            super(port);
            this.index = index;
        }
    }

    public static final class Unknown extends PortSpecific {
        Unknown(int port) {
            super(port);
        }
    }

    public static final class Waiting extends Ready {
        Waiting(int port, int index) {
            super(port, index);
        }
    }

    public static final class Scanning extends Ready {
        Scanning(int port, int index) {
            super(port, index);
        }
    }

    public static final class Hosting extends Ready {
        final String code;

        Hosting(int port, int index, String code) {
            super(port, index);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    public static final class Guesting extends Ready {
        final String url;
        final boolean ok;

        Guesting(int port, int index, String url, boolean ok) {
            super(port, index);
            this.url = url;
            this.ok = ok;
        }

        public String getUrl() {
            return url;
        }

        public boolean isOk() {
            return ok;
        }
    }

    public static final class Exception extends Ready {
        public enum Type {
            PING_HOST_FAIL,
            PING_HOST_RST,
            GUEST_ET_CRASH,
            HOST_ET_CRASH,
            PING_SERVER_RST
        }

        private final Type type;

        Exception(int port, int index, Type type) {
            super(port, index);
            this.type = type;
        }

        public Type getType() {
            return type;
        }
    }

    public static final class Fatal extends TerracottaState {
        static final Fatal INSTANCE = new Fatal();

        private Fatal() {
        }
    }
}
