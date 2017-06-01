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

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;

import com.spotify.helios.common.descriptors.HealthCheck;
import com.spotify.helios.testing.HeliosDeploymentResource;
import com.spotify.helios.testing.HeliosSoloDeployment;
import com.spotify.helios.testing.TemporaryJob;
import com.spotify.helios.testing.TemporaryJobs;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.describedAs;
import static org.hamcrest.Matchers.is;

public class MainIT {

  @ClassRule
  public static final HeliosDeploymentResource HELIOS = new HeliosDeploymentResource(
      HeliosSoloDeployment.fromEnv()
          .checkForNewImages(true)
          .removeHeliosSoloOnExit(true)
          .build());

  @Rule
  public final TemporaryJobs temporaryJobs = TemporaryJobs.builder()
      .client(HELIOS.client())
      .deployTimeoutMillis(TimeUnit.MINUTES.toMillis(1))
      .build();

  private URI frontend;
  private URI backend;

  @Before
  public void setUp() throws Exception {
    TemporaryJob backendJob = temporaryJobs.job()
        .image(Resources.toString(
            Resources.getResource("META-INF/docker/com.spotify.it/backend/image-name"),
            Charsets.UTF_8).trim())
        .port("http", 1337)
        .healthCheck(HealthCheck.newHttpHealthCheck()
            .setPath("/api/version")
            .setPort("http")
            .build()
        )
        .deploy();
    HostAndPort backendAddress = backendJob.address("http");
    backend = httpUri(backendAddress);

    TemporaryJob frontendJob = temporaryJobs.job()
        .image(Files.readFirstLine(new File("target/docker/image-name"),
                                   Charsets.UTF_8))
        .command(backend.toString())
        .port("http", 1338)
        .healthCheck(HealthCheck.newHttpHealthCheck()
            .setPath("/")
            .setPort("http")
            .build()
        )
        .deploy();
    HostAndPort frontendAddress = frontendJob.address("http");
    frontend = httpUri(frontendAddress);
  }

  private URI httpUri(HostAndPort frontendAddress) {
    return URI.create(
        String.format("http://%s:%d", frontendAddress.getHostText(), frontendAddress.getPort()));
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
