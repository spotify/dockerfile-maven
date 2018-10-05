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

package com.spotify.it.frontend;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.describedAs;
import static org.hamcrest.Matchers.is;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

public class MainIT {
  private static final Logger log = LoggerFactory.getLogger(MainIT.class);

  private static final int BACKEND_PORT = 1337;
  private static final int FRONTEND_PORT = 1338;

  /**
   * Create a Network that both containers are attached to. When the containers are setup, they have
   * .withNetworkAliases("foo") set, which allows the other container to refer to http://foo/ when
   * one container needs to talk to another.
   *
   * This is needed because container.getContainerIpAddress() is only for use for a test
   * to communicate with a container, not container-to-container communication -
   * getContainerIpAddress() will return "localhost" typically
   */
  @Rule public final Network network = Network.newNetwork();

  @Rule
  public final GenericContainer backendJob = createBackend(network);

  @Rule
  public final GenericContainer frontendJob = createFrontend(network);

  private GenericContainer createBackend(final Network network) {
    final String image;
    try {
      image = Resources.toString(
          Resources.getResource("META-INF/docker/com.spotify.it/backend/image-name"),
          Charsets.UTF_8).trim();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    final GenericContainer container = new GenericContainer(image)
        .withExposedPorts(BACKEND_PORT)
        .withNetwork(network)
        .withNetworkAliases("backend")
        .waitingFor(Wait.forHttp("/api/version"));

    // start early, since frontend needs to know the port of backend
    container.start();

    return container;
  }

  private GenericContainer createFrontend(final Network network) {
    final String image;
    try {
      image = Files.readFirstLine(new File("target/docker/image-name"), Charsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return new GenericContainer(image)
        .withExposedPorts(1338)
        .withCommand("http://backend:" + BACKEND_PORT)
        .withNetwork(network)
        .withNetworkAliases("frontend");
  }

  private URI frontend;
  private URI backend;

  @Before
  public void setUp() {
    backend = httpUri(backendJob, BACKEND_PORT);
    frontend = httpUri(frontendJob, FRONTEND_PORT);

    backendJob.followOutput(new Slf4jLogConsumer(
        LoggerFactory.getLogger(MainIT.class.getName() + ".backend")));

    frontendJob.followOutput(new Slf4jLogConsumer(
        LoggerFactory.getLogger(MainIT.class.getName() + ".frontend")));
  }

  private URI httpUri(GenericContainer container, int portNumber) {
    return URI.create("http://" + container.getContainerIpAddress()
                      + ":" + container.getMappedPort(portNumber));
  }

  @Test
  public void testVersion() throws Exception {
    String version = requestString(backend.resolve("/api/version"));
    String homepage = requestString(frontend.resolve("/"));

    assertThat(homepage, containsString("Backend version: " + version));
  }

  @Test
  public void testLowercase() throws Exception {
    String homepage;
    homepage = requestString(frontend.resolve("/"));
    Pattern pattern = Pattern.compile("Lower case of ([^ <]+) is according to backend ([^ <]+)");

    Matcher matcher = pattern.matcher(homepage);
    assertThat(matcher.find(), describedAs("the pattern was found", is(true)));
    assertThat(matcher.group(2), is(matcher.group(1).toLowerCase()));
  }

  private String requestString(URI uri) throws IOException {
    try (InputStream is = uri.toURL().openStream()) {
      return CharStreams.toString(new InputStreamReader(is, StandardCharsets.UTF_8));
    }
  }
}
