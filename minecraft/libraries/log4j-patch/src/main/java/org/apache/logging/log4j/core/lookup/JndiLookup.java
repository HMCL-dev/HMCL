/*
 * Copyright © 2021 Glavo <zjx001202@gmail.com>
 * This work is free. You can redistribute it and/or modify it under the
 * terms of the Do What The Fuck You Want To Public License, Version 2,
 * as published by Sam Hocevar. See http://www.wtfpl.net/ for more details.
 */
package org.apache.logging.log4j.core.lookup;

public class JndiLookup {
    public JndiLookup() {
        throw new NoClassDefFoundError("JNDI lookup is disabled. This is not an error. We are blocking some vulnerabilities through it. You should be able to play safely.");
    }
}
