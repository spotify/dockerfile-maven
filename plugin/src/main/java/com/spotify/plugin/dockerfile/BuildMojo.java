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

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.exceptions.ImageNotFoundException;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
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
   * Regex for a valid docker repository name.  Used in validateRepository().
   */
  private static final String VALID_REPO_REGEX = "^([a-z0-9_.-])+(:[0-9]+)?(\\/[a-z0-9_.-]+)*$";

  /**
   * Directory containing the the build context. This is typically the directory that contains
   * your Dockerfile.
   */
  @Parameter(defaultValue = "${project.basedir}",
      property = "dockerfile.contextDirectory",
      required = true)
  private File contextDirectory;

  /**
   * Path to the Dockerfile to build. The specified file must reside withing the build context
   */
  @Parameter(property = "dockerfile.dockerfile", required = false)
  private File dockerfile;

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

  @Parameter(property = "dockerfile.build.cacheFrom")
  private List<String> cacheFrom;

  @Parameter(property = "dockerfile.build.squash", defaultValue = "false")
  private boolean squash;

  @Override
  public void execute(DockerClient dockerClient)
      throws MojoExecutionException, MojoFailureException {
    final Log log = getLog();

    if (skipBuild) {
      log.info("Skipping execution because 'dockerfile.build.skip' is set");
      return;
    }

    log.info("dockerfile: " + dockerfile);
    log.info("contextDirectory: " + contextDirectory);

    Path dockerfilePath = null;
    if (dockerfile != null) {
      dockerfilePath = dockerfile.toPath();
    }
    final String imageId = buildImage(
        dockerClient, log, verbose, contextDirectory.toPath(), dockerfilePath, repository, tag, 
        pullNewerImage, noCache, buildArgs, cacheFrom, squash);

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
                           @Nonnull Path contextDirectory,
                           @Nullable Path dockerfile,
                           @Nullable String repository,
                           @Nonnull String tag,
                           boolean pullNewerImage,
                           boolean noCache,
                           @Nullable Map<String,String> buildArgs,
                           @Nullable List<String> cacheFrom,
                           boolean squash)
      throws MojoExecutionException, MojoFailureException {

    log.info(MessageFormat.format("Building Docker context {0}", contextDirectory));


    requireValidDockerFilePath(log, contextDirectory, dockerfile);

    final ArrayList<DockerClient.BuildParam> buildParameters = new ArrayList<>();
    if (dockerfile != null) {
      buildParameters.add(DockerClient.BuildParam.dockerfile(
          contextDirectory.relativize(dockerfile)));
    }

    final LoggingProgressHandler progressHandler = new LoggingProgressHandler(log, verbose);
    if (pullNewerImage) {
      buildParameters.add(DockerClient.BuildParam.pullNewerImage());
    }
    if (noCache) {
      buildParameters.add(DockerClient.BuildParam.noCache());
    }

    if (buildArgs != null && !buildArgs.isEmpty()) {
      buildParameters.add(new DockerClient.BuildParam("buildargs", encodeBuildParam(buildArgs)));
    }

    if (cacheFrom != null) {
      final List<String> cacheFromExistLocally = new ArrayList<>();
      for (String image : cacheFrom) {
        try {
          if (pullNewerImage || !imageExistLocally(dockerClient, image)) {
            dockerClient.pull(image);
          }
          log.info(MessageFormat.format("Build will use image {0} for cache-from", image));
          cacheFromExistLocally.add(image);
        } catch (ImageNotFoundException e) {
          log.warn(MessageFormat.format(
                  "Image {0} not found, build will not use it for cache-from", image));
        } catch (DockerException | InterruptedException e) {
          throw new MojoExecutionException("Could not pull cache-from image", e);
        }
      }
      if (!cacheFromExistLocally.isEmpty()) {
        buildParameters.add(new DockerClient.BuildParam("cache-from",
                encodeBuildParam(cacheFromExistLocally)));
      }
    }

    if (squash) {
      buildParameters.add(new DockerClient.BuildParam("squash", encodeBuildParam(squash)));
    }

    final DockerClient.BuildParam[] buildParametersArray =
        buildParameters.toArray(new DockerClient.BuildParam[buildParameters.size()]);

    log.info(""); // Spacing around build progress
    try {
      if (repository != null) {
        if (!validateRepository(repository)) {
          throw new MojoFailureException(
                  "Repo name \""
                          + repository
                          + "\" must contain only lowercase, numbers, '-', '_' or '.'.");
        }

        final String name = formatImageName(repository, tag);
        log.info(MessageFormat.format("Image will be built as {0}", name));
        log.info(""); // Spacing around build progress
        dockerClient.build(contextDirectory, name, progressHandler, buildParametersArray);
      } else {
        log.info("Image will be built without a name");
        log.info(""); // Spacing around build progress
        dockerClient.build(contextDirectory, progressHandler, buildParametersArray);
      }
    } catch (DockerException | IOException | InterruptedException e) {
      throw new MojoExecutionException("Could not build image", e);
    }
    log.info(""); // Spacing around build progress

    return progressHandler.builtImageId();
  }

  @VisibleForTesting
  static boolean validateRepository(@Nonnull String repository) {
    Pattern pattern = Pattern.compile(VALID_REPO_REGEX);
    return pattern.matcher(repository).matches();
  }

  private static void requireValidDockerFilePath(@Nonnull Log log,
                                                 @Nonnull Path contextDirectory,
                                                 @Nullable Path dockerfile)
      throws MojoFailureException {

    log.info("Path(dockerfile): " + dockerfile);
    log.info("Path(contextDirectory): " + contextDirectory);

    if (dockerfile == null
            && !Files.exists(contextDirectory.resolve("Dockerfile"))
            && !Files.exists(contextDirectory.resolve("dockerfile"))) {
      // user did not override the default value
      log.error("Missing Dockerfile in context directory: " + contextDirectory);
      throw new MojoFailureException("Missing Dockerfile in context directory: "
          + contextDirectory);
    }

    if (dockerfile != null) {
      if (!Files.exists(dockerfile)) {
        log.error("Missing Dockerfile at " + dockerfile);
        throw new MojoFailureException("Missing Dockerfile at " + dockerfile);
      }
      if (!dockerfile.startsWith(contextDirectory)) {
        log.error("Dockerfile " + dockerfile + " is not a child of the context directory: "
            + contextDirectory);
        throw new MojoFailureException("Dockerfile " + dockerfile
            + " is not a child of the context directory: " + contextDirectory);
      }
    }
  }
  
  private static String encodeBuildParam(Object buildParam) throws MojoExecutionException {
    try {
      return URLEncoder.encode(new Gson().toJson(buildParam), "utf-8");
    } catch (UnsupportedEncodingException e) {
      throw new MojoExecutionException("Could not build image", e);
    }
  }

  private static boolean imageExistLocally(DockerClient dockerClient, String image)
          throws DockerException, InterruptedException {
    try {
      dockerClient.inspectImage(image);
      return true;
    } catch (ImageNotFoundException e) {
      return false;
    }
  }

}
