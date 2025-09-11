package org.jackhuang.hmcl.terracotta;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import javafx.beans.property.ReadOnlyDoubleProperty;
import org.jackhuang.hmcl.terracotta.profile.TerracottaProfile;
import org.jackhuang.hmcl.util.gson.JsonSubtype;
import org.jackhuang.hmcl.util.gson.JsonType;
import org.jackhuang.hmcl.util.gson.TolerableValidationException;
import org.jackhuang.hmcl.util.gson.Validation;

import java.util.List;

public abstract class TerracottaState {
    protected TerracottaState() {
    }

    public boolean isUIFakeState() {
        return false;
    }

    public static final class Bootstrap extends TerracottaState {
        static final Bootstrap INSTANCE = new Bootstrap();

        private Bootstrap() {
        }
    }

    public static final class Uninitialized extends TerracottaState {
        private final boolean hasLegacy;

        Uninitialized(boolean hasLegacy) {
            this.hasLegacy = hasLegacy;
        }

        public boolean hasLegacy() {
            return hasLegacy;
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
        transient int port;

        protected PortSpecific(int port) {
            this.port = port;
        }
    }

    @JsonType(
            property = "state",
            subtypes = {
                    @JsonSubtype(clazz = Waiting.class, name = "waiting"),
                    @JsonSubtype(clazz = HostScanning.class, name = "host-scanning"),
                    @JsonSubtype(clazz = HostStarting.class, name = "host-starting"),
                    @JsonSubtype(clazz = HostOK.class, name = "host-ok"),
                    @JsonSubtype(clazz = GuestStarting.class, name = "guest-connecting"),
                    @JsonSubtype(clazz = GuestStarting.class, name = "guest-starting"),
                    @JsonSubtype(clazz = GuestOK.class, name = "guest-ok"),
                    @JsonSubtype(clazz = Exception.class, name = "exception"),
            }
    )
    static abstract class Ready extends PortSpecific {
        @SerializedName("index")
        final int index;

        @SerializedName("state")
        private final String state;

        Ready(int port, int index, String state) {
            super(port);
            this.index = index;
            this.state = state;
        }

        @Override
        public boolean isUIFakeState() {
            return this.index == -1;
        }
    }

    public static final class Unknown extends PortSpecific {
        Unknown(int port) {
            super(port);
        }
    }

    public static final class Waiting extends Ready {
        Waiting(int port, int index, String state) {
            super(port, index, state);
        }
    }

    public static final class HostScanning extends Ready {
        HostScanning(int port, int index, String state) {
            super(port, index, state);
        }
    }

    public static final class HostStarting extends Ready {
        HostStarting(int port, int index, String state) {
            super(port, index, state);
        }
    }

    public static final class HostOK extends Ready implements Validation {
        @SerializedName("room")
        private final String code;

        @SerializedName("profiles")
        private final List<TerracottaProfile> profiles;

        HostOK(int port, int index, String state, String code, List<TerracottaProfile> profiles) {
            super(port, index, state);
            this.code = code;
            this.profiles = profiles;
        }

        @Override
        public void validate() throws JsonParseException, TolerableValidationException {
            if (code == null) {
                throw new JsonParseException("code is null");
            }
            if (profiles == null) {
                throw new JsonParseException("profiles is null");
            }
        }

        public String getCode() {
            return code;
        }

        public List<TerracottaProfile> getProfiles() {
            return profiles;
        }
    }

    public static final class GuestStarting extends Ready {
        GuestStarting(int port, int index, String state) {
            super(port, index, state);
        }
    }

    public static final class GuestOK extends Ready implements Validation {
        @SerializedName("url")
        private final String url;

        @SerializedName("profiles")
        private final List<TerracottaProfile> profiles;

        GuestOK(int port, int index, String state, String url, List<TerracottaProfile> profiles) {
            super(port, index, state);
            this.url = url;
            this.profiles = profiles;
        }

        @Override
        public void validate() throws JsonParseException, TolerableValidationException {
            if (profiles == null) {
                throw new JsonParseException("profiles is null");
            }
        }

        public String getUrl() {
            return url;
        }

        public List<TerracottaProfile> getProfiles() {
            return profiles;
        }
    }

    public static final class Exception extends Ready implements Validation {
        public enum Type {
            PING_HOST_FAIL,
            PING_HOST_RST,
            GUEST_ET_CRASH,
            HOST_ET_CRASH,
            PING_SERVER_RST,
            SCAFFOLDING_INVALID_RESPONSE
        }

        private static final TerracottaState.Exception.Type[] LOOKUP = Type.values();

        @SerializedName("type")
        private final int type;

        Exception(int port, int index, String state, int type) {
            super(port, index, state);
            this.type = type;
        }

        @Override
        public void validate() throws JsonParseException, TolerableValidationException {
            if (type < 0 || type >= LOOKUP.length) {
                throw new JsonParseException(String.format("Type must between [0, %s)", LOOKUP.length));
            }
        }

        public Type getType() {
            return LOOKUP[type];
        }
    }

    public static final class Fatal extends TerracottaState {
        public enum Type {
            OS,
            NETWORK,
            INSTALL,
            TERRACOTTA,
            UNKNOWN;
        }

        private final Type type;

        public Fatal(Type type) {
            this.type = type;
        }

        public Type getType() {
            return type;
        }

        public boolean isRecoverable() {
            return this.type != Type.OS && this.type != Type.UNKNOWN;
        }
    }
}
