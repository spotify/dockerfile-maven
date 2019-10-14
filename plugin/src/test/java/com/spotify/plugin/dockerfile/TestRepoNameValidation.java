/*-
 * -\-\-
 * Dockerfile Maven Plugin
 * --
 * Copyright (C) 2019 Simon Woodward
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TestRepoNameValidation {
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testSuccess() throws MojoFailureException {
    assertTrue("All lower case should work", BuildMojo.validateRepository("alllowercase"));
    assertTrue("With numbers", BuildMojo.validateRepository("with000numbers"));
    assertTrue("Begin with numbers", BuildMojo.validateRepository("00withnumbers"));
    assertTrue("All numbers", BuildMojo.validateRepository("00757383"));
    assertTrue("End with numbers", BuildMojo.validateRepository("withnumbers34343"));
    assertTrue("With hyphens", BuildMojo.validateRepository("with-hyphens"));
    assertTrue("with underscores", BuildMojo.validateRepository("with_underscores"));
    assertTrue("With dots", BuildMojo.validateRepository("with.dots"));
    assertTrue("All underscores", BuildMojo.validateRepository("______"));
    assertTrue("All hyphens", BuildMojo.validateRepository("------"));
    assertTrue("All dots", BuildMojo.validateRepository("......"));
    assertTrue("Multipart", BuildMojo.validateRepository("example.com/okay./.path"));
    assertTrue("Start and end with dots", BuildMojo.validateRepository(".start.and.end."));
    assertTrue("Start and end with hyphens", BuildMojo.validateRepository("-start-and-end-"));
    assertTrue("Start and end with underscores", BuildMojo.validateRepository("_start_and_end_"));
    // Forward slash delimits the repo user from the repo name; strictly speaking,
    // you're allowed only one slash, somewhere in the middle.
    assertTrue("Multipart", BuildMojo.validateRepository("with/forwardslash"));
    assertTrue("Multi-multipart", BuildMojo.validateRepository("with/multiple/forwardslash"));
    // Local repositories can run on arbitrary ports
    assertTrue("Port number without slash", BuildMojo.validateRepository("example.com:8080"));
    assertTrue("Port number with slash", BuildMojo.validateRepository("example.com:8080/myrepo"));
  }

  @Test
  public void testFailCases() {
    assertFalse("Mixed case didn't fail", BuildMojo.validateRepository("ddddddDddddd"));
    assertFalse("Symbols didn't fail", BuildMojo.validateRepository("ddddddDd+dddd"));
    assertFalse("Starting slash didn't fail", BuildMojo.validateRepository("/atstart"));
    assertFalse("Ending slash didn't fail", BuildMojo.validateRepository("atend/"));
  }
}
