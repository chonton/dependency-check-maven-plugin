package org.honton.chas.dependency.analyzescope;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.artifact.filter.StrictPatternExcludesArtifactFilter;
import org.honton.chas.analyzer.api.DependencyAnalyzer;
import org.honton.chas.analyzer.api.LocationCollector;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class AnalyzeClassUsage {
  // fake artifact to hold classes from unknown artifact,  will usually hold classes from the jvm
  // rt.jar
  private static final Artifact TRANSITIVE = createTransitive();

  private final LocationCollector locationCollector;
  private final DependencyAnalyzer dependencyAnalyzer;
  // className to artifact mapping
  private final Map<String, Artifact> classNameToArtifact;
  // artifact to analyzed classes
  private final Map<Artifact, Map<String, Set<String>>> usedDependencies;
  // resolved className to artifact mapping
  private final Map<String, Artifact> resolvedClasses;
  // classNames provided by multiple artifacts
  private final Map<String, List<Artifact>> multipleDefinition;
  // dependencies that are implied by context
  private final List<Artifact> impliedDependencies;
  private final Set<Artifact> declaredButUnused;
  private final Set<Artifact> usedButUndeclared;

  AnalyzeClassUsage(LocationCollector locationCollector, DependencyAnalyzer dependencyAnalyzer) {
    this.locationCollector = locationCollector;
    this.dependencyAnalyzer = dependencyAnalyzer;

    classNameToArtifact = new HashMap<>();
    usedDependencies = new HashMap<>();
    resolvedClasses = new HashMap<>();

    multipleDefinition = new HashMap<>();
    impliedDependencies = new ArrayList<>();
    declaredButUnused = new HashSet<>();
    usedButUndeclared = new HashSet<>();
  }

  private static Artifact createTransitive() {
    return new DefaultArtifact(
        "unknown.groupId",
        "unknown-artifactId",
        "unknown.version",
        "compile",
        "jar",
        null,
        new DefaultArtifactHandler());
  }

  private static void logMissingFile(Log log, Artifact da) {
    log.info(da.getGroupId() + ':' + da.getArtifactId() + ':' + da.getVersion() + " does not have file");
  }

  /**
   * Add classNames available in a collection of Artifact to classNameToArtifact
   *
   * @param impliedDependencies The implicit artifacts
   */
  void addImpliedDependencies(Log log, Collection<Artifact> impliedDependencies) {
    for (Artifact artifact : impliedDependencies) {
      if (addClassesToArtifactMapping(log, artifact)) {
        this.impliedDependencies.add(artifact);
      }
    }
    this.impliedDependencies.add(TRANSITIVE);
  }

  /**
   * Add classNames available in a collection of Artifact to classNameToArtifact and add multiply
   * defined classNames to multipleDefinition
   *
   * @param dependencyArtifacts The artifacts to examine
   */
  void addDeclaredDependencies(Log log, Collection<Artifact> dependencyArtifacts) {
    for (Artifact artifact : dependencyArtifacts) {
      addClassesToArtifactMapping(log, artifact);
    }
  }

  private boolean addClassesToArtifactMapping(Log log, Artifact artifact) {
    File file = artifact.getFile();
    if (file == null) {
      logMissingFile(log, artifact);
      return false;
    }
    Set<String> classNames = locationCollector.list(file.toPath(), log);

    for (String className : classNames) {
      Artifact prior = classNameToArtifact.put(className, artifact);
      if (prior != null) {
        List<Artifact> artifacts =
            multipleDefinition.computeIfAbsent(
                className,
                cn -> {
                  List<Artifact> multiple = new ArrayList<>();
                  multiple.add(prior);
                  return multiple;
                });
        artifacts.add(artifact);
      }
    }
    return !classNames.isEmpty();
  }

  public void scanDeclaredDependencies(Set<Artifact> declaredDependencies) {
    for (Artifact declaredDependency : declaredDependencies) {
      if (!usedDependencies.containsKey(declaredDependency)) {
        declaredButUnused.add(declaredDependency);
      }
    }
  }

  /**
   * Add to the set of used dependencies. For each class in the given directory, find the referenced
   * class names. Find the referenced class names in the classNameToArtifact map, and add the
   * artifact to the used dependency set.
   *
   * @param artifact The artifact being examined
   */
  public void addUsedClassNames(Log log, Artifact artifact) {
    File file = artifact.getFile();
    if (file == null) {
      logMissingFile(log, artifact);
      return;
    }
    Map<String, Set<String>> classDependencies = dependencyAnalyzer.analyze(file.toPath(), log);
    if (!classDependencies.isEmpty()) {
      usedDependencies.put(artifact, classDependencies);
      for (Map.Entry<String, Set<String>> entry : classDependencies.entrySet()) {
        resolveClass(log, artifact, entry.getKey(), entry.getValue());
      }
    }
  }

  private void resolveClass(
      Log log, Artifact artifact, String className, Set<String> dependentClassNames) {
    Artifact prior = resolvedClasses.put(className, artifact);
    if (prior != null) {
      if (!prior.equals(artifact)) {
        log.error("Duplicate artifact for " + className + "; " + prior + " and " + artifact);
      }
    } else {
      for (String dependentClassName : dependentClassNames) {
        resolveDependentClass(log, dependentClassName);
      }
    }
  }

  private void resolveDependentClass(Log log, String dependentClassName) {
    Artifact dependent = findDependency(log, dependentClassName);
    if (dependent != null) {
      log.debug("Found " + dependentClassName + " in " + dependent);
      Map<String, Set<String>> analyzedArtifact =
          usedDependencies.computeIfAbsent(
              dependent, da -> {
                File file = da.getFile();
                if (file == null) {
                  logMissingFile(log, da);
                  return Map.of();
                }
                return dependencyAnalyzer.analyze(file.toPath(), log);
              });
      Set<String> dependentClasses = analyzedArtifact.get(dependentClassName);
      if (dependentClasses != null) {
        resolveClass(log, dependent, dependentClassName, dependentClasses);
      }
    }
  }

  private Artifact findDependency(Log log, String dependentClassName) {
    Artifact dependent = classNameToArtifact.get(dependentClassName);
    if (dependent != null) {
      return dependent.equals(TRANSITIVE) ? null : dependent;
    }

    if (!dependentClassName.startsWith("java.") && !dependentClassName.startsWith("javax.")) {
      log.debug("Could not find artifact containing " + dependentClassName);
    }
    resolvedClasses.put(dependentClassName, TRANSITIVE);
    classNameToArtifact.put(dependentClassName, TRANSITIVE);
    return null;
  }

  /**
   * Add each dependency in the declared-dependency set that is not in the used-dependency set to
   * the declared-but-unused list
   */
  public void setDeclaredDependencies(Collection<Artifact> declaredDependencies) {
    for (Artifact declaredDependency : declaredDependencies) {
      if (!usedDependencies.containsKey(declaredDependency)) {
        declaredButUnused.add(declaredDependency);
      }
    }
  }

  /**
   * Add each dependency in the used-dependency set that is not in the classpath-dependency set to
   * the used-but-undeclared set
   */
  public void setClasspathDependencies(Set<Artifact> classpathDependencies) {
    for (Artifact usedDependency : usedDependencies.keySet()) {
      if (!classpathDependencies.contains(usedDependency)) {
        usedButUndeclared.add(usedDependency);
      }
    }
  }

  /**
   * Remove any dependency from the declared-but-unused set that matches patterns
   */
  public void removeIgnoreUnusedDeclaredDependencies(
      List<String> ignoreUnusedDeclaredDependencies) {
    removeIgnored(declaredButUnused, ignoreUnusedDeclaredDependencies);
  }

  /**
   * Remove any dependency from the used-but-undeclared set that matches patterns
   */
  public void removeIgnoredUsedUndeclaredDependencies(
      List<String> ignoredUsedUndeclaredDependencies) {
    usedButUndeclared.removeAll(impliedDependencies);
    removeIgnored(usedButUndeclared, ignoredUsedUndeclaredDependencies);
  }

  private void removeIgnored(Set<Artifact> logSet, List<String> ignore) {
    if (ignore != null && !ignore.isEmpty()) {
      ArtifactFilter filter = new StrictPatternExcludesArtifactFilter(ignore);
      logSet.removeIf(artifact -> !filter.include(artifact));
    }
  }

  /**
   * log as warning the class name and artifacts the class is defined in.
   *
   * @return true, if warnings logged
   */
  public boolean logMultipleDefinitions(Log log) {
    if (multipleDefinition.isEmpty()) {
      return false;
    }
    for (Map.Entry<String, List<Artifact>> definition : multipleDefinition.entrySet()) {
      log.warn("Multiple definitions of " + definition.getKey());
      for (Artifact location : definition.getValue()) {
        log.warn("    " + location);
      }
    }
    return true;
  }

  public boolean logDeclaredButUnused(Log log) {
    return logCollectionContents(log, "Unused declared dependencies found:", declaredButUnused);
  }

  public boolean logUsedButUndeclared(Log log) {
    return logCollectionContents(log, "Used undeclared dependencies found:", usedButUndeclared);
  }

  private boolean logCollectionContents(Log log, String message, Set<Artifact> collection) {
    if (collection.isEmpty()) {
      return false;
    }

    log.warn(message);
    for (Artifact artifact : collection) {
      log.warn("    " + artifact);
    }
    return true;
  }
}
