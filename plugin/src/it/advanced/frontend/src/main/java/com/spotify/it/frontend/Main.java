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

import com.google.common.io.CharStreams;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Random;

import spark.Spark;

public class Main {

  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.println("Needs backend URL as command-line argument");
      System.exit(1);
      return;
    }

    URI backendUri = URI.create(args[0]);

    Random random = new SecureRandom();

    Spark.port(1338);
    Spark.get("/", (req, res) -> {
      String uppercase = new BigInteger(130, random).toString(32).toUpperCase();

      String version;
      try (InputStream versionStream = backendUri.resolve("/api/version").toURL().openStream()) {
        version =
            CharStreams.toString(new InputStreamReader(versionStream, StandardCharsets.UTF_8));
      }

      String lowercase;
      try (InputStream lowercaseStream =
               backendUri.resolve("/api/lowercase/" + uppercase).toURL().openStream()) {
        lowercase =
            CharStreams.toString(new InputStreamReader(lowercaseStream, StandardCharsets.UTF_8));
      }

      return "<!DOCTYPE html><html>"
             + "<head><title>frontend</title></head>"
             + "<body>"
             + "<p>Backend version: " + version + "</p>"
             + "<p>Lower case of " + uppercase + " is according to backend " + lowercase + "</p>"
             + "</body></html>";
    });
  }
}
