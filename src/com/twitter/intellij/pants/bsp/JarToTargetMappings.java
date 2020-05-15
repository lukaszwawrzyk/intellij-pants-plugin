// Copyright 2020 Pants project contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package com.twitter.intellij.pants.bsp;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public class JarToTargetMappings {
  private static final String SOURCES_JAR_SUFFIX = "-sources.jar";

  public static JarToTargetMappings getInstance(Project project) {
    return ServiceManager.getService(project, JarToTargetMappings.class);
  }

  public JarToTargetMappings(Project project) {
    this.project = project;
    initialize();
  }

  private final Project project;

  private Map<String, String> libraryJarToLibrarySourceJar;

  private void initialize() {
    try {
      Path path = Paths.get(project.getBasePath(), ".pants", "libraries.json");
      VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(path.toFile());
      String s = new String(file.contentsToByteArray());
      this.libraryJarToLibrarySourceJar = new Gson().fromJson(s, new TypeToken<Map<String, String>>(){}.getType());
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  public Optional<String> findSourceJarForLibraryJar(VirtualFile path) {
    return Optional.ofNullable(libraryJarToLibrarySourceJar.get(path.getPath()));
  }

  public Optional<String> findTargetForClassJar(VirtualFile jar) {
    if (!isProjectInternalDependency(jar)) {
      return Optional.empty();
    }
    String jarName = jar.getNameWithoutExtension();
    String targetAddress = targetAddressFromSanitizedFileName(jarName);
    return Optional.of(targetAddress);
  }

  public Optional<String> findSourceJarForTarget(String target) {
    String name = target.replaceAll("[:/]", ".") + SOURCES_JAR_SUFFIX;
    Path path = bloopJarsPath(project).resolve(name);
    return Optional.of(path.toString());
  }

  public Optional<String> findTargetForSourceJar(VirtualFile jar) {
    if (!isProjectInternalDependency(jar)) {
      return Optional.empty();
    }
    String name = jar.getName();
    if (name.endsWith(SOURCES_JAR_SUFFIX)) {
      String withoutSuffix = name.substring(0, name.length() - SOURCES_JAR_SUFFIX.length());
      return Optional.of(targetAddressFromSanitizedFileName(withoutSuffix));
    } else {
      return Optional.empty();
    }
  }

  private String targetAddressFromSanitizedFileName(String jarName) {
    String[] components = jarName.split("\\.");
    String[] targetPath = Arrays.copyOf(components, components.length - 1);
    String targetName = components[components.length - 1];
    return String.join("/", targetPath) + ":" + targetName;
  }

  private boolean isProjectInternalDependency(VirtualFile jar) {
    Path jarPath = Paths.get(jar.getPath());
    Path bloopJarsPath = bloopJarsPath(project);
    return jarPath.startsWith(bloopJarsPath);
  }

  private static Path bloopJarsPath(Project project) {
    return Paths.get(project.getBasePath(), ".bloop", "bloop-jars");
  }

}
