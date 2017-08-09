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
File imageIdFile = new File(basedir, "target/docker/image-id")
assert imageIdFile.isFile()

File repositoryFile = new File(basedir, "target/docker/repository")
assert repositoryFile.text == "test/build-into-tag\n"

File tagFile = new File(basedir, "target/docker/tag")
assert tagFile.text == "latest\n"

File imageNameFile = new File(basedir, "target/docker/image-name")
assert imageNameFile.text == "test/build-into-tag:latest\n"
