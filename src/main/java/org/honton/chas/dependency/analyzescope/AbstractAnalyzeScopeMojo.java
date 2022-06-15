package org.honton.chas.dependency.analyzescope;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.honton.chas.analyzer.api.LocationCollector;
import org.honton.chas.analyzer.api.DependencyAnalyzer;
import org.honton.chas.analyzer.asm.AsmLocationCollector;
import org.honton.chas.analyzer.asm.AsmDependencyAnalyzer;

public abstract class AbstractAnalyzeScopeMojo extends AbstractMojo {
  /** The Maven project to analyze. */
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  MavenProject project;

  /** Whether to fail the build if a dependency warning is found. */
  @Parameter(property = "dependency-check.fail", defaultValue = "true")
  private boolean failOnWarning;

  /** Skip plugin execution completely. */
  @Parameter(property = "dependency-check.skip", defaultValue = "false")
  boolean skip;

  /**
   * List of dependencies to ignore. Any dependency on this list will be excluded from
   * the "declared but unused" and the "used but undeclared" lists. The filter syntax is:
   *
   * <pre>
   * [groupId]:[artifactId]:[type]:[version]
   * </pre>
   *
   * where each pattern segment is optional and supports full and partial <code>*</code> wildcards.
   * An empty pattern segment is treated as an implicit wildcard. *
   *
   * <p>For example, <code>org.apache.*</code> will match all artifacts whose group id starts with
   * <code>org.apache.</code>, and <code>:::*-SNAPSHOT</code> will match all snapshot artifacts.
   */
  @Parameter private List<String> ignoreDependencies;

  /**
   * List of dependencies that will be ignored if they are used but undeclared. The filter syntax
   * is:
   *
   * <pre>
   * [groupId]:[artifactId]:[type]:[version]
   * </pre>
   *
   * where each pattern segment is optional and supports full and partial <code>*</code> wildcards.
   * An empty pattern segment is treated as an implicit wildcard. *
   *
   * <p>For example, <code>org.apache.*</code> will match all artifacts whose group id starts with
   * <code>org.apache.</code>, and <code>:::*-SNAPSHOT</code> will match all snapshot artifacts.
   */
  @Parameter private List<String> ignoreUsedUndeclaredDependencies;

  /**
   * List of dependencies that will be ignored if they are declared but unused. The filter syntax
   * is:
   *
   * <pre>
   * [groupId]:[artifactId]:[type]:[version]
   * </pre>
   *
   * where each pattern segment is optional and supports full and partial <code>*</code> wildcards.
   * An empty pattern segment is treated as an implicit wildcard. *
   *
   * <p>For example, <code>org.junit.jupiter.*</code> will match all artifacts whose group id starts
   * with <code>org.junit.jupiter.</code>, and <code>:::*-SNAPSHOT</code> will match all snapshot
   * artifacts.
   */
  @Parameter private List<String> ignoreUnusedDeclaredDependencies;

  private LocationCollector locationCollector = new AsmLocationCollector();
  private DependencyAnalyzer dependencyAnalyzer = new AsmDependencyAnalyzer();

  // Mojo methods -----------------------------------------------------------

  /*
   * @see Mojo#execute()
   */
  @Override
  public void execute() throws MojoExecutionException {
    if (skip()) {
      return;
    }

    boolean warning = checkDependencies();

    if (warning && failOnWarning) {
      throw new MojoExecutionException("Dependency problems found");
    }
  }

  abstract boolean skip();

  abstract Artifact workingArtifact();

  abstract Collection<Artifact> impliedArtifacts();

  abstract Set<String> getDeclaredScopes();

  abstract Set<String> getClasspathScopes();

  // private methods --------------------------------------------------------

  private Set<Artifact> getDependencyArtifactsByScope(Collection<String> acceptableScopes) {
    Set<Artifact> scopedArtifacts = new HashSet<>();
    for (Artifact artifact : project.getDependencyArtifacts()) {
      if (acceptableScopes.contains(artifact.getScope())) {
        scopedArtifacts.add(artifact);
      }
    }
    return scopedArtifacts;
  }

  private boolean checkDependencies() throws MojoExecutionException {
    try {
      AnalyzeClassUsage analyzeClassUsage = analyzeClassUsage();
      boolean reported = logAnalysis(analyzeClassUsage);

      if (!reported) {
        getLog().info("No dependency problems found");
      }

      return reported;
    } catch (IOException ioException) {
      throw new MojoExecutionException("Analysis failed", ioException);
    }
  }

  private AnalyzeClassUsage analyzeClassUsage() throws IOException {
    Artifact artifact = project.getArtifact();
    if (artifact.getFile() == null) {
      String outputDirectory = project.getBuild().getOutputDirectory();
      getLog().warn("Setting File " + outputDirectory + " for Artifact " + artifact);
      artifact.setFile(new File(outputDirectory));
    }

    AnalyzeClassUsage analyzer = new AnalyzeClassUsage(locationCollector, dependencyAnalyzer);
    analyzer.addImpliedDependencies(getLog(), impliedArtifacts());

    Set<String> acceptableScopes = getDeclaredScopes();
    Set<Artifact> declaredDependencies = getDependencyArtifactsByScope(acceptableScopes);
    analyzer.addDeclaredDependencies(getLog(), declaredDependencies);

    // Determine the set of classes required to compile the sources. These classes are the
    // used-classes set.
    // Determine in which dependencies each used class is present.  Add these dependencies to
    // used-dependencies
    analyzer.addUsedClassNames(getLog(), workingArtifact());

    // The declared-dependency set contains the main-artifact and the compile-scope dependencies
    // from  resolver
    analyzer.scanDeclaredDependencies(declaredDependencies);

    // Add each dependency in declared-dependencies that is not in used-dependencies to
    // declared-but-unused
    analyzer.setDeclaredDependencies(declaredDependencies);
    // Remove any dependency that matches patterns declared in the ignoreUnusedDeclaredDependencies
    // parameter
    analyzer.removeIgnoreUnusedDeclaredDependencies(ignoreUnusedDeclaredDependencies);
    analyzer.removeIgnoreUnusedDeclaredDependencies(ignoreDependencies);

    Set<String> classpathScopes = getClasspathScopes();
    Set<Artifact> classpathDependencies = getDependencyArtifactsByScope(classpathScopes);
    classpathDependencies.add(artifact);

    // Add each dependency in used-dependencies that is not in classpath-dependencies to
    // used-but-undeclared
    analyzer.setClasspathDependencies(classpathDependencies);
    // Remove any dependency that matches patterns declared in the  ignoreUsedUndeclaredDependencies
    // parameter
    analyzer.removeIgnoredUsedUndeclaredDependencies(ignoreUsedUndeclaredDependencies);
    analyzer.removeIgnoredUsedUndeclaredDependencies(ignoreDependencies);

    return analyzer;
  }

  private boolean logAnalysis(AnalyzeClassUsage analyzeClassUsage) {
    // If the multiple-definition map is non-empty, log the class name and artifacts the class is
    // defined in.
    boolean reported = analyzeClassUsage.logMultipleDefinitions(getLog());
    // log remaining members of declared-but-unused set to the console
    reported |= analyzeClassUsage.logDeclaredButUnused(getLog());
    // log remaining members of used-but-undeclared set to the console
    reported |= analyzeClassUsage.logUsedButUndeclared(getLog());
    return reported;
  }
}
