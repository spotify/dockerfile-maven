/*-
 * -\-\-
 * Dockerfile Maven Plugin
 * --
 * Copyright (C) 2018 David Wimsey
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

package com.spotify.plugin.dockerfile.kubernetes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class KubernetesUtilities {

    private static final Logger log = LoggerFactory.getLogger(KubernetesUtilities.class);

    /**
     * Name of the executable to call when attempting to discovery kubernetes authentication information
     */
    public static final String OPENSHIFT_CLI_BINARY_NAME = "oc";

    /**
     * When used as a password in your maven servers definition, this keyword will cause the OC command to be used to obtain a token instead the value of the password member
     */
    public static final String OPENSHIFT_CLI_PASSWORD_KEYWORD = "oc";


    public KubernetesUtilities() {
    }

    private static Boolean foundOcCommand = null;

    public static boolean hasOpenShift() {
        if (foundOcCommand == null) {
            final String helpCmd = OPENSHIFT_CLI_BINARY_NAME + " help";
            try {
                log.debug("Running OpenShift help command: " + helpCmd);
                final Process proc = Runtime.getRuntime().exec(helpCmd);

                log.debug("Waiting for OpenShift help command to complete");
                proc.waitFor();
                log.trace("OpenShift help command has completed");

                foundOcCommand = (proc.exitValue() == 0 ? true : false);
                log.debug("OpenShift help command returned: {}", proc.exitValue());
            } catch (Exception e) {
                log.info("Could not run OpenShift help command: " + helpCmd + ": Exception: {}", e);
                foundOcCommand = false;
            }
        }
        return foundOcCommand;
    }

    public static String callKubectl(final String flag) {
        String authenticationToken = null;
        Runtime rt = Runtime.getRuntime();
        Process proc = null;
        final String ocCmd = OPENSHIFT_CLI_BINARY_NAME + " whoami " + flag;

        try {
            proc = rt.exec(ocCmd);
        } catch (IOException e) {
            log.error("Error Calling kubernetes command: " + ocCmd + ": {}", e);
            return null;
        }

        int exitVal = -2;
        try (InputStream stderr = proc.getErrorStream()) {
            try (InputStreamReader stderrReader = new InputStreamReader(stderr)) {
                try (BufferedReader bufferedStderrReader = new BufferedReader(stderrReader)) {
                    try (InputStream stdout = proc.getInputStream()) {
                        try (InputStreamReader stdoutReader = new InputStreamReader(stdout)) {
                            try (BufferedReader bufferedStdoutReader = new BufferedReader(stdoutReader)) {
                                try {
                                    exitVal = -1;
                                    exitVal = proc.waitFor();

                                    authenticationToken = bufferedStdoutReader.readLine();
                                    if (exitVal == 0) {
                                        return authenticationToken;
                                    }
                                    log.error("Invalid response from kubernetes command token: ExitValue: " + exitVal + " Token: " + authenticationToken + " STDERR: " + bufferedStderrReader.readLine());
                                } catch (InterruptedException e) {
                                    log.error("Could not read kubernetes authentication token: {}", e);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            log.error("Could not obtain OpenShift authentication token: {}", ioe);
        }

        return authenticationToken;
    }

    public static String getAuthenticationToken(final String serverName) {
        return callKubectl("-t");
    }

    public static String getAuthenticationUsername(final String serverName) {
        return callKubectl("");
    }

    public static String getAuthenticationContext(final String serverName) {
        return callKubectl("-c");
    }

    public static String getAuthenticationServer(final String serverName) {
        return callKubectl("--show-server=true");
    }

}
