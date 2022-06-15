package org.honton.chas.dependency.analyzescope;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Analyzes the main dependencies of this project and determines which are: used and declared; used
 * and undeclared; unused and declared. This goal is intended to be used in the build lifecycle,
 * thus it assumes that the <code>compile</code> phase has been executed.
 *
 * @see AnalyzeTestMojo
 */
@Mojo(
    name = "main",
    defaultPhase = LifecyclePhase.PROCESS_CLASSES,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    threadSafe = true)
public class AnalyzeMainMojo extends AbstractAnalyzeScopeMojo {

  @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
  File mainClasses;

  @Override
  boolean skip() {
    if (skip) {
      getLog().info("Skipping dependency-check:main execution");
      return true;
    }
    if (mainClasses == null || !mainClasses.exists()) {
      getLog().info("No main classes directory");
      return true;
    }
    return false;
  }

  @Override
  Artifact workingArtifact() {
    return project.getArtifact();
  }

  @Override
  Collection<Artifact> impliedArtifacts() {
    return Collections.singletonList(workingArtifact());
  }

  @Override
  Set<String> getDeclaredScopes() {
    return Collections.singleton(Artifact.SCOPE_COMPILE);
  }

  @Override
  Set<String> getClasspathScopes() {
    return Set.of(Artifact.SCOPE_COMPILE, Artifact.SCOPE_PROVIDED, Artifact.SCOPE_SYSTEM);
  }
}
