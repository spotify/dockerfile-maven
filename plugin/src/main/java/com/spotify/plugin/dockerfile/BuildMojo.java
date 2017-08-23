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

import com.google.gson.Gson;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "build",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresProject = true,
    threadSafe = true)
public class BuildMojo extends AbstractDockerMojo {

  /**
   * Directory containing the Dockerfile to build.
   */
  @Parameter(defaultValue = "${project.basedir}",
      property = "dockerfile.contextDirectory",
      required = true)
  private File contextDirectory;

  /**
   * The repository to put the built image into when building the Dockerfile, for example
   * <tt>spotify/foo</tt>.  You should also set the <tt>tag</tt> parameter, otherwise the tag
   * <tt>latest</tt> is used by default.  If this is not specified, the <tt>tag</tt> goal needs to
   * be ran separately in order to tag the generated image with anything.
   */
  @Parameter(property = "dockerfile.repository")
  private String repository;

  /**
   * The tag to apply when building the Dockerfile, which is appended to the repository.
   */
  @Parameter(property = "dockerfile.tag", defaultValue = "latest")
  private String tag;

  /**
   * What to tag snapshots as default {@code latest}.
   */
  @Parameter(property = "dockerfile.snapShotTag")
  private String snapShotTag;

  /**
   * Tag releases as latest.
   */
  @Parameter(property = "dockerfile.tagReleaseAsLatest", defaultValue = "false")
  private boolean tagReleaseAsLatest;

  /**
   * Disables the build goal; it becomes a no-op.
   */
  @Parameter(property = "dockerfile.build.skip", defaultValue = "false")
  private boolean skipBuild;

  /**
   * Updates base images automatically.
   */
  @Parameter(property = "dockerfile.build.pullNewerImage", defaultValue = "true")
  private boolean pullNewerImage;

  /**
   * Do not use cache when building the image.
   */
  @Parameter(property = "dockerfile.build.noCache", defaultValue = "false")
  private boolean noCache;

  /**
   * Custom build arguments.
   */
  @Parameter(property = "dockerfile.buildArgs")
  private Map<String,String> buildArgs;

  @Override
  public void execute(DockerClient dockerClient)
      throws MojoExecutionException, MojoFailureException {
    final Log log = getLog();

    if (skipBuild) {
      log.info("Skipping execution because 'dockerfile.build.skip' is set");
      return;
    }

    // Is the tag a SNAPSHOT maven version
    boolean isSnapshot = tag.endsWith("-SNAPSHOT");

    // default is null
    String alternativeTag = null;

    if ( !isSnapshot && tagReleaseAsLatest && tag.equals(project.getVersion()) ) {
      alternativeTag = "latest";
      log.info(MessageFormat.format(
          "Image is a RELEASE and will be additionally tagged as: {0}", alternativeTag));
    }

    if ( isSnapshot && tag.equals(project.getVersion()) && !snapShotTag.isEmpty()) {
      tag = snapShotTag;
      log.info(MessageFormat.format("Image is a SNAPSHOT and will be tagged as: {0}", tag));
    }

    final String imageId = buildImage(
        dockerClient, log, verbose, contextDirectory, repository, tag,  alternativeTag,
        pullNewerImage, noCache, buildArgs);

    if (imageId == null) {
      log.warn("Docker build was successful, but no image was built");
    } else {
      log.info(MessageFormat.format("Detected build of image with id {0}", imageId));
      writeMetadata(Metadata.IMAGE_ID, imageId);
    }

    // Do this after the build so that other goals don't use the tag if it doesn't exist
    if (repository != null) {
      writeImageInfo(repository, tag);
    }

    if ( alternativeTag != null ) {
      writeMetadata(Metadata.ALTERNATIVE_TAG, alternativeTag);
    }

    writeMetadata(log);

    if (repository == null) {
      log.info(MessageFormat.format("Successfully built {0}", imageId));
    } else {
      log.info(MessageFormat.format("Successfully built {0}", formatImageName(repository, tag)));
    }
  }

  @Nullable
  static String buildImage(@Nonnull DockerClient dockerClient,
                           @Nonnull Log log,
                           boolean verbose,
                           @Nonnull File contextDirectory,
                           @Nullable String repository,
                           @Nonnull String tag,
                           @Nullable String alternativeTag,
                           boolean pullNewerImage,
                           boolean noCache,
                           @Nullable Map<String,String> buildArgs)
      throws MojoExecutionException, MojoFailureException {

    log.info(MessageFormat.format("Building Docker context {0}", contextDirectory));

    if (!new File(contextDirectory, "Dockerfile").exists()
        && !new File(contextDirectory, "dockerfile").exists()) {
      log.error("Missing Dockerfile in context directory: " + contextDirectory.getPath());
      throw new MojoFailureException("Missing Dockerfile in context directory: "
                                     + contextDirectory.getPath());
    }

    final LoggingProgressHandler progressHandler = new LoggingProgressHandler(log, verbose);
    final ArrayList<DockerClient.BuildParam> buildParameters = new ArrayList<>();
    if (pullNewerImage) {
      buildParameters.add(DockerClient.BuildParam.pullNewerImage());
    }
    if (noCache) {
      buildParameters.add(DockerClient.BuildParam.noCache());
    }

    if (buildArgs != null && !buildArgs.isEmpty()) {
      try {
        final String encodedBuildArgs = URLEncoder.encode(new Gson().toJson(buildArgs), "utf-8");
        buildParameters.add(new DockerClient.BuildParam("buildargs", encodedBuildArgs));
      } catch (UnsupportedEncodingException e) {
        throw new MojoExecutionException("Could not build image", e);
      }
    }

    final DockerClient.BuildParam[] buildParametersArray =
        buildParameters.toArray(new DockerClient.BuildParam[buildParameters.size()]);

    log.info(""); // Spacing around build progress
    try {
      if (repository != null) {
        final String name = formatImageName(repository, tag);
        log.info(MessageFormat.format("Image will be built as {0}", name));
        log.info(""); // Spacing around build progress
        dockerClient.build(contextDirectory.toPath(), name, progressHandler, buildParametersArray);
        if (alternativeTag != null) {
          final String alternativeName = formatImageName(repository, alternativeTag);
          log.info(MessageFormat.format("Image will be additionally tagged as {0}",
                                        alternativeName));
          dockerClient.tag(name, alternativeName);
        }
      } else {
        log.info("Image will be built without a name");
        log.info(""); // Spacing around build progress
        dockerClient.build(contextDirectory.toPath(), progressHandler, buildParametersArray);
      }
    } catch (DockerException | IOException | InterruptedException e) {
      throw new MojoExecutionException("Could not build image", e);
    }
    log.info(""); // Spacing around build progress

    return progressHandler.builtImageId();
  }

}
