/*
 * -/-/-
 * Dockerfile Maven Plugin
 * %%
 * Copyright (C) 2015 - 2016 Spotify AB
 * %%
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
 * -\-\-
 */
File imageIdFile = new File(basedir, "b/target/docker-info-deps/META-INF/docker/com.spotify.it/a/image-id")
assert imageIdFile.isFile()

File repositoryFile = new File(basedir, "b/target/docker-info-deps/META-INF/docker/com.spotify.it/a/repository")
assert repositoryFile.text == "test/multi-module-a\n"

File tagFile = new File(basedir, "b/target/docker-info-deps/META-INF/docker/com.spotify.it/a/tag")
assert tagFile.text == "unstable\n"

File imageNameFile = new File(basedir, "b/target/docker-info-deps/META-INF/docker/com.spotify.it/a/image-name")
assert imageNameFile.text == "test/multi-module-a:unstable\n"

String buildLog = new File("${basedir}/build.log").getText("UTF-8")
assert buildLog.contains("docker-maven plugin execution is skipped for:")
