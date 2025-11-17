package org.honton.chas.dependency.collector;

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.maven.plugin.logging.Log;
import org.honton.chas.dependency.analyzer.ClassFileAnalyzer;

/**
 * Analyze a set of classes at a location. Find all class names that are referenced by the classes
 * at location.
 */
@RequiredArgsConstructor
public class DependencyCollector extends PathCollector {

  /** the Jar or directory to analyze */
  private final Path location;

  /** The logger for any processing messages */
  private final Log log;

  private final Map<ClassDesc, Set<ClassDesc>> classDependencies = new HashMap<>();

  public Map<ClassDesc, Set<ClassDesc>> getClassDependencies() {
    listPath(location);
    return classDependencies;
  }

  @Override
  protected void exceptionHandler(String name, IOException exception) {
    log.warn("Could not analyze " + name + " within " + location);
  }

  @Override
  protected void visitClass(ClassDesc classDesc, BytesSupplier contentSupplier) throws IOException {
    classDependencies.put(classDesc, new ClassFileAnalyzer(contentSupplier).getDependencies());
  }
}
