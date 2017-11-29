/*-
 * -\-\-
 * Dockerfile Maven Plugin
 * --
 * Copyright (C) 2017 Spotify AB
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

import com.spotify.docker.client.auth.RegistryAuthSupplier;
import com.spotify.docker.client.messages.RegistryAuth;
import com.spotify.docker.client.messages.RegistryConfigs;

import java.util.HashMap;
import java.util.Map;

public class MavenPomAuthSupplier implements RegistryAuthSupplier {

  private String userName;
  private String password;

  public MavenPomAuthSupplier(String userName, String password) {
    this.userName = userName;
    this.password = password;
  }

  public boolean hasUserName() {
    return this.userName != null && !this.userName.equals("");
  }

  @Override
  public RegistryAuth authFor(String server) {
    if (hasUserName()) {
      return RegistryAuth.builder()
          .username(this.userName)
          .password(this.password)
          .build();
    }
    return null;
  }

  @Override
  public RegistryAuth authForSwarm() {
    return null;
  }

  @Override
  public RegistryConfigs authForBuild() {
    final Map<String, RegistryAuth> allConfigs = new HashMap<>();
    if (hasUserName()) {
      allConfigs.put(
          "config",
          RegistryAuth.builder()
              .username(this.userName)
              .password(this.password)
              .build()
      );
    }
    return RegistryConfigs.create(allConfigs);
  }
}
