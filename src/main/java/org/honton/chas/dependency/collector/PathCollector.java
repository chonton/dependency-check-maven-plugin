package org.honton.chas.dependency.collector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import org.codehaus.plexus.util.DirectoryScanner;

/** Utility to visit classes in a library given either as a jar file or an exploded directory. */
public abstract class PathCollector {

  private static final String DOT_CLASS = ".class";

  protected abstract void exceptionHandler(String name, IOException exception);

  protected abstract void visitClass(ClassDesc classDesc, BytesSupplier contentSupplier)
      throws IOException;

  /**
   * Analyze all classes in a directory or jar.
   *
   * @param path The directory or jar to scan for classes.
   */
  protected void listPath(Path path) {
    if (Files.isDirectory(path)) {
      listDirectory(path);
    } else if (Files.isReadable(path)) {
      listJar(path);
    } else {
      throw new IllegalArgumentException(
          "Location " + path + " is not a directory and not a readable jar");
    }
  }

  private void listJar(Path jar) {
    try {
      listJar(Files.newInputStream(jar));
    } catch (IOException ioException) {
      exceptionHandler(jar.toString(), ioException);
    }
  }

  private void listJar(InputStream is) throws IOException {
    try (JarInputStream in = new JarInputStream(is)) {
      JarEntry entry;
      while ((entry = in.getNextJarEntry()) != null) {
        String path = entry.getName();
        if (!path.endsWith(DOT_CLASS)) {
          continue;
        }
        if (path.indexOf('-') >= 0) {
          // no module-info
          continue;
        }
        String className = pathToClassName(path);
        visitClass(ClassDesc.of(className), in::readAllBytes);
      }
    }
  }

  private void listDirectory(Path directory) {
    DirectoryScanner scanner = new DirectoryScanner();

    scanner.setBasedir(directory.toFile());
    scanner.setIncludes(new String[] {"**/*.class"});
    scanner.scan();

    String[] paths = scanner.getIncludedFiles();
    for (String path : paths) {
      String className = pathToClassName(path.replace(File.separatorChar, '/'));
      acceptClassInDirectory(directory, path, className);
    }
  }

  private void acceptClassInDirectory(Path directory, String path, String className) {
    Path classLocation = directory.resolve(path);
    try {
      visitClass(ClassDesc.of(className), () -> Files.readAllBytes(classLocation));
    } catch (IOException ioException) {
      exceptionHandler(className, ioException);
    }
  }

  private String pathToClassName(String path) {
    return path.substring(0, path.length() - DOT_CLASS.length()).replace('/', '.');
  }

  @FunctionalInterface
  public interface BytesSupplier {

    byte[] contents() throws IOException;
  }
}
