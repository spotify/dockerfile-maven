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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION;

import com.spotify.docker.client.ImageRef;
import com.spotify.docker.client.auth.RegistryAuthSupplier;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.RegistryAuth;
import com.spotify.docker.client.messages.RegistryConfigs;
import com.spotify.plugin.dockerfile.kubernetes.KubernetesUtilities;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.cipher.PlexusCipherException;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;
import org.sonatype.plexus.components.sec.dispatcher.SecUtil;
import org.sonatype.plexus.components.sec.dispatcher.model.SettingsSecurity;

public class MavenRegistryAuthSupplier implements RegistryAuthSupplier {

  private static final Logger log = LoggerFactory.getLogger(MavenRegistryAuthSupplier.class);

  private final Settings settings;

  public MavenRegistryAuthSupplier(final Settings settings) {
    this.settings = settings;
  }

  @Override
  public RegistryAuth authFor(final String imageName) throws DockerException {
    final ImageRef ref = new ImageRef(imageName);
    Server server = settings.getServer(ref.getRegistryName());
    if (server != null) {
      String password = server.getPassword();
      boolean encrypted = false;
      try {
        encrypted = isEncrypted(password);
      } catch (PlexusCipherException e) {
        log.warn("Couldn't determine if Maven server password is encrypted.");
        log.warn("Assuming Maven server password *is not* encrypted.", e);
      }
      try {
        log.debug("Maven server password is encrypted: {}", encrypted);
        if (encrypted) {
          password = decryptPassword(password);
          log.debug("Successfully decrypted Maven server password");
        }
      } catch (PlexusCipherException | SecDispatcherException e) {
        throw new DockerException("Failed to decrypt Maven server password", e);
      }

      if(KubernetesUtilities.OPENSHIFT_CLI_PASSWORD_KEYWORD.compareToIgnoreCase(password) == 0) {
        log.debug("Looking up kubernetes token");
        if(!KubernetesUtilities.hasOpenShift()) {
          throw new DockerException("Maven server configuration specified \"" + KubernetesUtilities.OPENSHIFT_CLI_PASSWORD_KEYWORD + "\" keyword but could not find OpenShift control command: " + KubernetesUtilities.OPENSHIFT_CLI_BINARY_NAME);
        }
        String authenticationToken = KubernetesUtilities.getAuthenticationToken(server.getId());
        if(authenticationToken != null) {
          log.debug("Returning kubernetes token");
          return RegistryAuth.builder()
                  .username(server.getUsername())
                  .password(authenticationToken)
                  .build();

        } else {
          throw new DockerException("Maven server configuration specified \"" + KubernetesUtilities.OPENSHIFT_CLI_PASSWORD_KEYWORD + "\" keyword but no password was returned.");
        }
      }

      return RegistryAuth.builder()
        .username(server.getUsername())
        .password(password)
        .build();
    }
    log.warn("Did not find maven server configuration for docker server " + ref.getRegistryName());
    return null;
  }

  @Override
  public RegistryAuth authForSwarm() throws DockerException {
    return null;
  }

  @Override
  public RegistryConfigs authForBuild() throws DockerException {
    final Map<String, RegistryAuth> allConfigs = new HashMap<>();
    for (Server server : settings.getServers()) {
      if(KubernetesUtilities.OPENSHIFT_CLI_PASSWORD_KEYWORD.compareToIgnoreCase(server.getPassword()) == 0) {
        log.debug("Looking up kubernetes token");
        if(!KubernetesUtilities.hasOpenShift()) {
          throw new DockerException("Maven server configuration specified \"" + KubernetesUtilities.OPENSHIFT_CLI_PASSWORD_KEYWORD + "\" keyword but could not find OpenShift control command: " + KubernetesUtilities.OPENSHIFT_CLI_BINARY_NAME);
        }
        String authenticationToken = KubernetesUtilities.getAuthenticationToken(server.getId());
        if(authenticationToken != null) {
          log.debug("Returning kubernetes token");
          allConfigs.put(
                  server.getId(),
                  RegistryAuth.builder()
                          .username(server.getUsername())
                          .password(authenticationToken)
                          .build()
          );
        } else {
          throw new DockerException("Maven server configuration specified \"" + KubernetesUtilities.OPENSHIFT_CLI_PASSWORD_KEYWORD + "\" keyword but no password was returned.");
        }
      } else {
        allConfigs.put(
                server.getId(),
                RegistryAuth.builder()
                        .username(server.getUsername())
                        .password(server.getPassword())
                        .build()
        );
      }
    }
    return RegistryConfigs.create(allConfigs);
  }

  /**
   * Decrypts the supplied Maven server password using the master password from {@link
   * SettingsSecurity}.
   *
   * @param encryptedPassword the encrypted server password
   * @return the decrypted server password
   * @throws PlexusCipherException if decryption fails
   * @throws SecDispatcherException if {@link SettingsSecurity} can't be read
   */
  private String decryptPassword(String encryptedPassword)
      throws PlexusCipherException, SecDispatcherException {
    // Use -Dsettings.security=... to override the location of the security settings XML file.
    String location = System
        .getProperty(SYSTEM_PROPERTY_SEC_LOCATION, "~/.m2/settings-security.xml");
    checkState(!isNullOrEmpty(location), "Location of settings-security.xml must not be empty");
    String realLocation = location.charAt(0) == '~'
        ? System.getProperty("user.home") + location.substring(1)
        : location;
    log.debug("Using location of '{}' for settings-security.xml",
        new File(realLocation).getAbsolutePath());
    SettingsSecurity settingsSecurity = SecUtil.read(realLocation, true);
    String encryptedMasterPassword = settingsSecurity.getMaster();
    String decryptedMasterPassword = decryptPassword(encryptedMasterPassword,
        DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION);
    return decryptPassword(encryptedPassword, decryptedMasterPassword);
  }

  /**
   * Decrypts a Maven server password.
   *
   * @param encryptedPassword the encrypted server password
   * @param passPhrase the password used to encrypt the server password
   * @return the decrypted password
   * @throws PlexusCipherException if decryption fails
   */
  private static String decryptPassword(String encryptedPassword, String passPhrase)
      throws PlexusCipherException {
    DefaultPlexusCipher cipher = new DefaultPlexusCipher();
    return cipher.decryptDecorated(encryptedPassword, passPhrase);
  }

  /**
   * Determines if the supplied Maven server password is encrypted or not.
   *
   * @param password the password to test
   * @return true if the password is encrypted, otherwise false
   * @see <a href=https://maven.apache.org/guides/mini/guide-encryption.html">Password
   *     Encryption</a>
   * @throws PlexusCipherException if the decryption algorithm isn't available
   */
  private static boolean isEncrypted(String password) throws PlexusCipherException {
    DefaultPlexusCipher cipher = new DefaultPlexusCipher();
    return cipher.isEncryptedString(password);
  }
}
