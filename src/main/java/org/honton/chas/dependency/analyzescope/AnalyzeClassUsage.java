package org.honton.chas.dependency.analyzescope;

import java.io.File;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.artifact.filter.StrictPatternExcludesArtifactFilter;
import org.honton.chas.dependency.collector.DependencyCollector;
import org.honton.chas.dependency.collector.LocationCollector;

class AnalyzeClassUsage {

  // fake artifact to hold classes from unknown artifact,  will usually hold classes from the jvm
  // rt.jar
  private static final Artifact TRANSITIVE = createTransitive();

  // className to artifact mapping
  private final Map<ClassDesc, Artifact> classNameToArtifact;
  // artifact to analyzed classes
  private final Map<Artifact, Map<ClassDesc, Set<ClassDesc>>> usedDependencies;
  // resolved className to artifact mapping
  private final Map<ClassDesc, Artifact> resolvedClasses;
  // classNames provided by multiple artifacts
  private final Map<ClassDesc, List<Artifact>> multipleDefinition;
  // dependencies that are implied by context
  private final List<Artifact> impliedDependencies;
  private final Set<Artifact> declaredButUnused;
  private final Set<Artifact> usedButUndeclared;

  AnalyzeClassUsage() {
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
    log.info(
        da.getGroupId() + ':' + da.getArtifactId() + ':' + da.getVersion() + " does not have file");
  }

  private static String className(ClassDesc cd) {
    return cd.packageName().isEmpty()
        ? cd.displayName()
        : cd.packageName() + '.' + cd.displayName();
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
    Set<ClassDesc> classNames = new LocationCollector(file.toPath(), log).list();

    for (ClassDesc className : classNames) {
      Artifact prior = classNameToArtifact.put(className, artifact);
      if (prior != null) {
        List<Artifact> artifacts =
            multipleDefinition.computeIfAbsent(
                className,
                _ -> {
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
    Map<ClassDesc, Set<ClassDesc>> classDependencies =
        new DependencyCollector(file.toPath(), log).getClassDependencies();
    if (!classDependencies.isEmpty()) {
      usedDependencies.put(artifact, classDependencies);
      for (Map.Entry<ClassDesc, Set<ClassDesc>> entry : classDependencies.entrySet()) {
        resolveClass(log, artifact, entry.getKey(), entry.getValue());
      }
    }
  }

  private void resolveClass(
      Log log, Artifact artifact, ClassDesc className, Set<ClassDesc> dependentClassDescs) {
    Artifact prior = resolvedClasses.put(className, artifact);
    if (prior != null) {
      if (!prior.equals(artifact)) {
        log.error("Duplicate artifact for " + className + "; " + prior + " and " + artifact);
      }
    } else {
      for (ClassDesc dependentClassDesc : dependentClassDescs) {
        resolveDependentClass(log, dependentClassDesc);
      }
    }
  }

  private void resolveDependentClass(Log log, ClassDesc dependentClassDesc) {
    Artifact dependent = findDependency(log, dependentClassDesc);
    if (dependent != null) {
      log.debug("Found " + dependentClassDesc + " in " + dependent);
      Map<ClassDesc, Set<ClassDesc>> analyzedArtifact =
          usedDependencies.computeIfAbsent(
              dependent,
              da -> {
                File file = da.getFile();
                if (file == null) {
                  logMissingFile(log, da);
                  return Map.of();
                }
                return new DependencyCollector(file.toPath(), log).getClassDependencies();
              });
      Set<ClassDesc> dependentClasses = analyzedArtifact.get(dependentClassDesc);
      if (dependentClasses != null) {
        resolveClass(log, dependent, dependentClassDesc, dependentClasses);
      }
    }
  }

  private Artifact findDependency(Log log, ClassDesc dependentClassDesc) {
    Artifact dependent = classNameToArtifact.get(dependentClassDesc);
    if (dependent != null) {
      return dependent.equals(TRANSITIVE) ? null : dependent;
    }

    String packageName = dependentClassDesc.packageName();
    if (!packageName.equals("java") && !packageName.equals("javax")) {
      log.debug("Could not find artifact containing " + dependentClassDesc);
    }
    resolvedClasses.put(dependentClassDesc, TRANSITIVE);
    classNameToArtifact.put(dependentClassDesc, TRANSITIVE);
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

  /** Remove any dependency from the declared-but-unused set that matches patterns */
  public void removeIgnoreUnusedDeclaredDependencies(
      List<String> ignoreUnusedDeclaredDependencies) {
    removeIgnored(declaredButUnused, ignoreUnusedDeclaredDependencies);
  }

  /** Remove any dependency from the used-but-undeclared set that matches patterns */
  public void removeIgnoredUsedUndeclaredDependencies(
      List<String> ignoredUsedUndeclaredDependencies) {
    impliedDependencies.forEach(usedButUndeclared::remove);
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
    for (Map.Entry<ClassDesc, List<Artifact>> definition : multipleDefinition.entrySet()) {
      log.warn("Multiple definitions of " + className(definition.getKey()));
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
