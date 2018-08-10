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

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "rmi",
    defaultPhase = LifecyclePhase.PRE_CLEAN,
    requiresProject = true,
    threadSafe = true)
public class RemoveImageMojo extends AbstractDockerMojo {

  /**
   * Whether to force removal of the image.
   */
  @Parameter(property = "dockerfile.rmi.force", defaultValue = "false")
  private boolean forceRemove;

  /**
   * Whether to delete untagged parents.
   */
  @Parameter(property = "dockerfile.rmi.prune", defaultValue = "true")
  private boolean prune;

  /**
   * Disables the tag goal; it becomes a no-op.
   */
  @Parameter(property = "dockerfile.rmi.skip", defaultValue = "false")
  private boolean skipTag;

  @Override
  protected void execute(DockerClient dockerClient)
      throws MojoExecutionException, MojoFailureException {
    final Log log = getLog();

    if (skipTag) {
      log.info("Skipping execution because 'dockerfile.rmi.skip' is set");
      return;
    }

    final String imageId = readMetadata(Metadata.IMAGE_ID);
    if (imageId == null) {
      log.info("No Docker image was built: Nothing to remove.");
      return;
    }

    final StringBuilder messageBuilder = new StringBuilder();
    messageBuilder.append("Removing image ");
    messageBuilder.append(imageId);
    if (forceRemove) {
      messageBuilder.append(" with all tags");
    }
    if (prune) {
      messageBuilder.append(", deleting untagged parents");
    }
    log.info(messageBuilder.toString());

    try {
      dockerClient.removeImage(imageId, forceRemove, !prune);
    } catch (DockerException | InterruptedException e) {
      throw new MojoExecutionException("Could not remove Docker image", e);
    }
  }
}
