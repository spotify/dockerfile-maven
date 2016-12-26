/*-
 * -\-\-
 * Dockerfile Maven Plugin
 * --
 * Copyright (C) 2016 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

package com.spotify.plugin.dockerfile;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.jar.JarArchiver;

public abstract class AbstractDockerMojo extends AbstractMojo {

  protected enum Metadata {
    IMAGE_ID("image ID", "image-id"),
    REPOSITORY("repository", "repository"),
    TAG("tag", "tag"),
    IMAGE_NAME("image name", "image-name");

    private final String friendlyName;
    private final String fileName;

    Metadata(String friendlyName, String fileName) {
      this.friendlyName = friendlyName;
      this.fileName = fileName;
    }

    public String getFriendlyName() {
      return friendlyName;
    }

    public String getFileName() {
      return fileName;
    }
  }

  /**
   * Directory containing the generated Docker info JAR.
   */
  @Parameter(defaultValue = "${project.build.directory}",
      property = "dockerfile.outputDirectory",
      required = true)
  private File buildDirectory;

  /**
   * Directory where various Docker-related metadata fragments will be stored.
   */
  @Parameter(defaultValue = "${project.build.directory}/docker",
      property = "dockerfile.dockerInfoDirectory", required = true)
  protected File dockerInfoDirectory;

  /**
   * Directory where test metadata will be written during build.
   */
  @Parameter(defaultValue = "${project.build.testOutputDirectory}",
      property = "dockerfile.testOutputDirectory",
      required = true)
  protected File testOutputDirectory;

  @Parameter(defaultValue = "300000" /* 5 minutes */,
      property = "dockerfile.readTimeoutMillis",
      required = true)
  protected long readTimeoutMillis;

  @Parameter(defaultValue = "300000" /* 5 minutes */,
      property = "dockerfile.connectTimeoutMillis",
      required = true)
  protected long connectTimeoutMillis;

  /**
   * Certain Docker operations can fail due to mysterious Docker daemon conditions.  Sometimes it
   * might be worth it to just retry operations until they succeed.  This parameter controls how
   * many times operations should be retried before they fail.  By default, an extra attempt (so up
   * to two attempts) is made before failing.
   */
  @Parameter(defaultValue = "1", property = "dockerfile.retryCount")
  protected int retryCount;

  /**
   * Whether to output a verbose log when performing various operations.
   */
  @Parameter(defaultValue = "false", property = "dockerfile.verbose")
  protected boolean verbose;

  /**
   * Disables the entire dockerfile plugin; all goals become no-ops.
   */
  @Parameter(defaultValue = "false", property = "dockerfile.skip")
  protected boolean skip;

  /**
   * Whether to write image information into the test output directory, so that docker information
   * is available on the CLASSPATH for integration tests.
   */
  @Parameter(defaultValue = "true", property = "dockerfile.writeTestMetadata")
  protected boolean writeTestMetadata;

  /**
   * Require the jar plugin to build a new Docker info JAR even if none of the contents appear to
   * have changed.  By default, this plugin looks to see if the output jar exists and inputs have
   * not changed.  If these conditions are true, the plugin skips creation of the jar.  This does
   * not work when other plugins, like the maven-shade-plugin, are configured to post-process the
   * jar.  This plugin can not detect the post-processing, and so leaves the post-processed jar in
   * place.  This can lead to failures when those plugins do not expect to find their own output as
   * an input.  Set this parameter to <tt>true</tt> to avoid these problems by forcing this plugin
   * to recreate the jar every time.
   */
  @Parameter(defaultValue = "false", property = "dockerfile.forceCreation")
  private boolean forceCreation;

  /**
   * Name of the generated Docker info JAR.
   */
  @Parameter(defaultValue = "${project.build.finalName}", property = "dockerfile.finalName")
  private String finalName;

  /**
   * Classifier to use when attaching the Docker info JAR.  If empty or absent, the JAR will become
   * the main artifact of the project.
   */
  @Parameter(defaultValue = "docker-info", property = "dockerfile.classifier")
  protected String classifier;

  /**
   * The Maven project.
   */
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  protected MavenProject project;

  /**
   * The current Maven session.
   */
  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

  /**
   * The archive configuration to use for the Docker info JAR.  This can be used to embed additional
   * information in the JAR.
   */
  @Parameter
  private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

  /**
   * The JAR archiver.
   */
  @Component(role = Archiver.class, hint = "jar")
  private JarArchiver jarArchiver;

  /**
   * The Maven project helper.
   */
  @Component
  private MavenProjectHelper projectHelper;

  protected abstract void execute(DockerClient dockerClient)
      throws MojoExecutionException, MojoFailureException;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Skipping execution because 'dockerfile.skip' is set");
    } else {
      tryExecute(this.retryCount + 1); // We want to try at least one time
    }
  }

  private void tryExecute(int attempts) throws MojoFailureException, MojoExecutionException {
    Preconditions.checkArgument(attempts > 0, "attempts must not be negative");

    MojoExecutionException exception = null;

    for (int attempt = 0; attempt < attempts; attempt++) {
      try {
        execute(openDockerClient());
        return; // Not "break;" since we don't want to "throw exception;"
      } catch (MojoExecutionException e) {
        // Don't catch MojoFailureException, since that exception means "permanent failure"
        exception = e;

        final int attemptsLeft = attempts - attempt - 1;
        if (attemptsLeft > 0) {
          final String warningMessage =
              MessageFormat.format("An attempt failed, will retry {0} more times", attemptsLeft);
          getLog().warn(warningMessage, e);
        }
      }
    }

    throw exception;
  }

  protected void writeMetadata(Log log) throws MojoExecutionException {
    writeTestMetadata();
    final File jarFile = buildDockerInfoJar(log);
    attachJar(jarFile);
  }

  protected void writeMetadata(@Nonnull Metadata metadata, @Nonnull String value)
      throws MojoExecutionException {
    final File metadataFile = ensureMetadataFile(metadata);

    final String oldValue = readMetadata(metadata);
    if (Objects.equals(oldValue, value)) {
      return;
    }

    try {
      Files.write(value + "\n", metadataFile, Charsets.UTF_8);
    } catch (IOException e) {
      final String message =
          MessageFormat.format("Could not write {0} file at {1}", metadata.getFriendlyName(),
              metadataFile);
      throw new MojoExecutionException(message, e);
    }
  }

  private void writeTestMetadata() throws MojoExecutionException {
    if (writeTestMetadata && dockerInfoDirectory.exists()) {
      final File testMetadataDir = new File(testOutputDirectory, getMetaSubdir());

      if (!testMetadataDir.isDirectory()) {
        if (!testMetadataDir.mkdirs()) {
          throw new MojoExecutionException("Could not create metadata output directory");
        }
      }

      for (String name : dockerInfoDirectory.list()) {
        final File sourceFile = new File(dockerInfoDirectory, name);
        final File targetFile = new File(testMetadataDir, name);
        try {
          Files.copy(sourceFile, targetFile);
        } catch (IOException e) {
          throw new MojoExecutionException("Could not copy files", e);
        }
      }
    }
  }

  private String getMetaSubdir() {
    return String.format("META-INF/docker/%s/%s/", project.getGroupId(), project.getArtifactId());
  }

  void attachJar(@Nonnull File jarFile) {
    if (classifier != null) {
      projectHelper.attachArtifact(project, "docker-info", classifier, jarFile);
    } else {
      project.getArtifact().setFile(jarFile);
    }
  }

  @Nonnull
  protected File buildDockerInfoJar(@Nonnull Log log) throws MojoExecutionException {
    final File jarFile = getJarFile(buildDirectory, finalName, classifier);

    final MavenArchiver archiver = new MavenArchiver();
    archiver.setArchiver(jarArchiver);
    archiver.setOutputFile(jarFile);

    archive.setForced(forceCreation);

    if (dockerInfoDirectory.exists()) {
      final String prefix = getMetaSubdir();
      archiver.getArchiver().addDirectory(dockerInfoDirectory, prefix);
    } else {
      log.warn("Docker info directory not created - Docker info JAR will be empty");
    }

    try {
      archiver.createArchive(session, project, archive);
    } catch (Exception e) {
      throw new MojoExecutionException("Could not build Docker info JAR", e);
    }

    return jarFile;
  }

  @Nonnull
  private static File getJarFile(@Nonnull File basedir, @Nonnull String finalName,
                                 @Nullable String classifier) {
    if (classifier == null) {
      classifier = "";
    } else {
      classifier = classifier.trim();
    }

    if (classifier.length() > 0 && !classifier.startsWith("-")) {
      classifier = "-" + classifier;
    }

    return new File(basedir, finalName + classifier + ".jar");
  }

  @Nonnull
  protected File ensureDockerInfoDirectory() throws MojoExecutionException {
    if (!dockerInfoDirectory.exists()) {
      if (!dockerInfoDirectory.mkdirs()) {
        throw new MojoExecutionException(
            MessageFormat
                .format("Could not create Docker info directory {0}", dockerInfoDirectory));
      }
    }
    return dockerInfoDirectory;
  }

  @Nonnull
  protected File ensureMetadataFile(@Nonnull Metadata metadata) throws MojoExecutionException {
    return new File(ensureDockerInfoDirectory(), metadata.getFileName());
  }

  protected void writeImageInfo(String repository, String tag) throws MojoExecutionException {
    writeMetadata(Metadata.REPOSITORY, repository);
    writeMetadata(Metadata.TAG, tag);
    writeMetadata(Metadata.IMAGE_NAME, formatImageName(repository, tag));
  }

  @Nullable
  protected String readMetadata(@Nonnull Metadata metadata) throws MojoExecutionException {
    final File metadataFile = ensureMetadataFile(metadata);

    if (!metadataFile.exists()) {
      return null;
    }

    try {
      return Files.readFirstLine(metadataFile, Charsets.UTF_8);
    } catch (IOException e) {
      final String message =
          MessageFormat.format("Could not read {0} file at {1}", metadata.getFileName(),
                               metadataFile);
      throw new MojoExecutionException(message, e);
    }
  }

  @Nonnull
  protected static String formatImageName(@Nonnull String repository, @Nonnull String tag) {
    return repository + ":" + tag;
  }

  @Nonnull
  protected DockerClient openDockerClient() throws MojoExecutionException {
    try {
      return DefaultDockerClient.fromEnv()
          .readTimeoutMillis(readTimeoutMillis)
          .connectTimeoutMillis(connectTimeoutMillis)
          .build();
    } catch (DockerCertificateException e) {
      throw new MojoExecutionException("Could not load Docker certificates", e);
    }
  }
}
