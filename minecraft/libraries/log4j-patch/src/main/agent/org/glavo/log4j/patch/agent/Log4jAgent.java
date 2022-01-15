package org.glavo.log4j.patch.agent;

import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.util.Arrays;

public final class Log4jAgent {
    static final String JNDI_LOOKUP_CLASS_NAME = "org/apache/logging/log4j/core/lookup/JndiLookup";
    static final String INTERPOLATOR_CLASS_NAME = "org/apache/logging/log4j/core/lookup/Interpolator";

    static final byte[] INTERPOLATOR_CLASS_SHA = {53, 103, 16, 123, 51, 29, 65, -70, -32, 71, -11, 7, 114, -15, 72, 127, 40, -38, 35, 18};

    static boolean isBeta = false;

    private static byte[] loadResource(String name) {
        try {
            try (InputStream input = Log4jAgent.class.getResourceAsStream(name)) {
                if (input == null) {
                    throw new AssertionError(name + " not found");
                }
                int available = input.available();
                if (available <= 0) {
                    throw new AssertionError();
                }
                byte[] res = new byte[available];
                if (input.read(res) != available) {
                    throw new AssertionError();
                }

                return res;
            }
        } catch (Exception ex) {
            throw new InternalError(ex);
        }
    }

    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        if ("true".equals(agentArgs)) {
            isBeta = true;
        }
        inst.addTransformer(new Transformer());
    }

    private static final class Transformer implements ClassFileTransformer {

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
            if (!isBeta && JNDI_LOOKUP_CLASS_NAME.equals(className)) {
                return loadResource("JndiLookup.class.bin");
            }
            if (isBeta && INTERPOLATOR_CLASS_NAME.equals(className)) {
                try {
                    MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                    sha1.update(classfileBuffer);
                    if (Arrays.equals(INTERPOLATOR_CLASS_SHA, sha1.digest())) {
                        return loadResource("Interpolator.class.bin");
                    }
                } catch (NoSuchAlgorithmException e) {
                    throw new InternalError(e);
                }
            }
            return null;
        }
    }

}
