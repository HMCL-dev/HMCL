package java.lang;

import java.util.Set;

/**
 * Dummy java compatibility class
 *
 * @author xxDark
 */
public abstract class ModuleLayer {

  //CHECKSTYLE:OFF
  public Set<Module> modules() {
    throw new UnsupportedOperationException();
  }
  public static ModuleLayer boot() {
    throw new UnsupportedOperationException();
  }
  //CHECKSTYLE:ON
}
