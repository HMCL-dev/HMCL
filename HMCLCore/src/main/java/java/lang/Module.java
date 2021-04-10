package java.lang;

import java.util.Set;

/**
 * Dummy java compatibility class
 *
 * @author Matt
 */
public abstract class Module {

  //CHECKSTYLE:OFF
  public ModuleLayer getLayer() { throw new UnsupportedOperationException(); }
  public Set<String> getPackages() { throw new UnsupportedOperationException(); }
  //CHECKSTYLE:ON
}
