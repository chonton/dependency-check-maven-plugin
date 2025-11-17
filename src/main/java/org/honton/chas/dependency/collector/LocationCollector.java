package org.honton.chas.dependency.collector;

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.maven.plugin.logging.Log;

/**
 * List the set of classes contained in a library given either as a jar file or an exploded
 * directory.
 */
@RequiredArgsConstructor
public class LocationCollector extends PathCollector {

  /** the Jar or directory to analyze */
  private final Path location;

  /** The logger for any processing messages */
  private final Log log;

  private final Set<ClassDesc> classNames = new HashSet<>();

  /**
   * List the set of classes present at a location.
   *
   * @return The names of the classes at the location
   */
  public Set<ClassDesc> list() {
    listPath(location);
    return classNames;
  }

  @Override
  protected void exceptionHandler(String className, IOException exception) {
    log.warn("Could not list " + className + " at location " + location);
  }

  @Override
  protected void visitClass(ClassDesc classDesc, BytesSupplier contentSupplier) {
    classNames.add(classDesc);
  }
}
