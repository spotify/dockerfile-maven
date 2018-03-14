# Dockerfile Maven

[![Build Status](https://travis-ci.org/spotify/dockerfile-maven.svg?branch=master)](https://travis-ci.org/spotify/dockerfile-maven)
[![Maven Central](https://img.shields.io/maven-central/v/com.spotify/dockerfile-maven.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.spotify%22%20dockerfile-maven)
[![License](https://img.shields.io/github/license/spotify/dockerfile-maven.svg)](LICENSE)

This is a Maven plugin and extension which help to seamlessly
integrate Docker with Maven.

The design goals are:

  - Don't try to do anything fancy.  `Dockerfile`s are how you build
    Docker projects; that's what this plugin uses.  They are
    mandatory.
  - Make the Docker build process integrate with the Maven build
    process.  If you bind the default phases, when you type `mvn
    package`, you get a Docker image.  When you type `mvn deploy`,
    your image gets pushed.
  - Make the goals remember what you are doing.  You can type `mvn
    dockerfile:build` and later `mvn dockerfile:tag` and later `mvn
    dockerfile:push` without problems.  This also eliminates the need
    for something like `mvn dockerfile:build -DalsoPush`; instead you
    can just say `mvn dockerfile:build dockerfile:push`.
  - Integrate with the Maven build reactor.  You can depend on the
    Docker image of one project in another project, and Maven will
    build the projects in the correct order.  This is useful when you
    want to run integration tests involving multiple services.

This project adheres to the [Open Code of Conduct][code-of-conduct].
By participating, you are expected to honor this code.

See the [changelog for a list of releases][changelog]

[code-of-conduct]: https://github.com/spotify/code-of-conduct/blob/master/code-of-conduct.md
[changelog]: CHANGELOG.md

## Set-up

This plugin requires Java 7 or later, and Apache Maven 3 or later.  To
run the integration tests or to use the plugin in practice, a working
Docker set-up is needed.

## Example

For more examples, see the [integration test](./plugin/src/it) directory.

In particular, the [advanced](./plugin/src/it/advanced) test showcases a
full service consisting of two micro-services that are integration
tested using `helios-testing`.

This configures the actual plugin to build your image with `mvn
package` and push it with `mvn deploy`.  Of course you can also say
`mvn dockerfile:build` explicitly.

```xml
<plugin>
  <groupId>com.spotify</groupId>
  <artifactId>dockerfile-maven-plugin</artifactId>
  <version>${dockerfile-maven-version}</version>
  <executions>
    <execution>
      <id>default</id>
      <goals>
        <goal>build</goal>
        <goal>push</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <repository>spotify/foobar</repository>
    <tag>${project.version}</tag>
    <buildArgs>
      <JAR_FILE>${project.build.finalName}.jar</JAR_FILE>
    </buildArgs>
  </configuration>
</plugin>
```

A corresponding `Dockerfile` could look like:

```
FROM openjdk:8-jre
MAINTAINER David Flemström <dflemstr@spotify.com>

ENTRYPOINT ["/usr/bin/java", "-jar", "/usr/share/myservice/myservice.jar"]

# Add Maven dependencies (not shaded into the artifact; Docker-cached)
ADD target/lib           /usr/share/myservice/lib
# Add the service itself
ARG JAR_FILE
ADD target/${JAR_FILE} /usr/share/myservice/myservice.jar
```

## What does it give me?

There are many advantages to using this plugin for your builds.

### Faster build times

This plugin lets you leverage Docker cache more consistently, vastly
speeding up your builds by letting you cache Maven dependencies in
your image.  It also encourages avoiding the `maven-shade-plugin`,
which also greatly speeds up builds.

### Consistent build lifecycle

You no longer have to say something like:

    mvn package
    mvn dockerfile:build
    mvn verify
    mvn dockerfile:push
    mvn deploy

Instead, it is simply enough to say:

    mvn deploy

With the basic configuration, this will make sure that the image is
built and pushed at the correct times.

### Depend on Docker images of other services

You can depend on the Docker information of another project, because
this plugin attaches project metadata when it builds Docker images.
Simply add this information to any project:

```xml
<dependency>
  <groupId>com.spotify</groupId>
  <artifactId>foobar</artifactId>
  <version>1.0-SNAPSHOT</version>
  <type>docker-info</type>
</dependency>
```

Now, you can read information about the Docker image of the project
that you depended on:

```java
String imageName = getResource("META-INF/docker/com.spotify/foobar/image-name");
```

This is great for an integration test where you want the latest
version of another project's Docker image.

Note that you have to register a Maven extension in your POM (or a
parent POM) in order for the `docker-info` type to be supported:

```xml
<build>
  <extensions>
    <extension>
      <groupId>com.spotify</groupId>
      <artifactId>dockerfile-maven-extension</artifactId>
      <version>${version}</version>
    </extension>
  </extensions>
</build>
```

## Use other Docker tools that rely on Dockerfiles

Your project(s) look like so:

```
a/
  Dockerfile
  pom.xml
b/
  Dockerfile
  pom.xml
```

You can now use these projects with Fig or docker-compose or some
other system that works with Dockerfiles.  For example, a
`docker-compose.yml` might look like:

```yaml
service-a:
  build: a/
  ports:
  - '80'

service-b:
  build: b/
  links:
  - service-a
```

Now, `docker-compose up` and `docker-compose build` will work as
expected.

## Authentication and private Docker registry support

Since version 1.3.0, the plugin will automatically use any configuration in
your `~/.dockercfg` or `~/.docker/config.json` file when pulling, pushing, or
building images to private registries.

Additionally the plugin will enable support for Google Container Registry if it
is able to successfully load [Google's "Application Default Credentials"][ADC].
The plugin will also load Google credentials from the file pointed to by the
environment variable `DOCKER_GOOGLE_CREDENTIALS` if it is defined. Since GCR
authentication requires retrieving short-lived access codes for the given
credentials, support for this registry is baked into the underlying
docker-client rather than having to first populate the docker config file
before running the plugin.

[ADC]: https://developers.google.com/identity/protocols/application-default-credentials

GCR users may need to initialize their Application Default Credentials via `gcloud`.
Depending on where the plugin will run, they may wish to use [their Google
identity][app-def-login] by running the following command

    gcloud auth application-default login

or [create a service account][service-acct] instead.

[app-def-login]: https://cloud.google.com/sdk/gcloud/reference/auth/application-default/login
[service-acct]: https://cloud.google.com/docs/authentication/getting-started#creating_a_service_account

## Authenticating with maven settings.xml

Since version 1.3.6, you can authenticate using your maven settings.xml instead
of docker configuration.  Just add configuration similar to:

```xml
<configuration>
  <repository>docker-repo.example.com:8080/organization/image</repository>
  <tag>latest</tag>
  <useMavenSettingsForAuth>true</useMavenSettingsForAuth>
</configuration>
```

You can also use `-Ddockerfile.useMavenSettingsForAuth=true` on the command line.

Then, in your maven settings file, add configuration for the server:

```xml
<servers>
  <server>
    <id>docker-repo.example.com:8080</id>
    <username>me</username>
    <password>mypassword</password>
  </server>
</servers>
```

exactly as you would for any other server configuration.

## Authenticating with maven pom.xml

Since version 1.3.XX, you can authenticate using config from the pom itself.
Just add configuration similar to:

```xml
 <plugin>
    <groupId>com.spotify</groupId>
    <artifactId>dockerfile-maven-plugin</artifactId>
    <version>${version}</version>
    <configuration>
        <username>repoUserName</username>
        <password>repoPassword</password>
        <repository>${docker.image.prefix}/${project.artifactId}</repository>
        <buildArgs>
            <JAR_FILE>target/${project.build.finalName}.jar</JAR_FILE>
        </buildArgs>
    </configuration>
</plugin>
```
or simpler, 
```xml
 <plugin>
    <groupId>com.spotify</groupId>
    <artifactId>dockerfile-maven-plugin</artifactId>
    <version>${version}</version>
    <configuration>
        <repository>${docker.image.prefix}/${project.artifactId}</repository>
        <buildArgs>
            <JAR_FILE>target/${project.build.finalName}.jar</JAR_FILE>
        </buildArgs>
    </configuration>
</plugin>
```

with this command line call

    mvn goal -Ddockerfile.username=... -Ddockerfile.password=...
    
## Maven Goals

Goals available for this plugin:

| Goal      | Description    | Default Phase |
| ---- | ---- | ---- |
| `dockerfile:build` | Builds a Docker image from a Dockerfile. | `package` |
| `dockerfile:tag` | Tags a Docker image. | `package` |
| `dockerfile:push` | Pushes a Docker image to a repository. | `deploy` |

## Skip Docker Goals Bound to Maven Phases

You can pass options to maven to disable the docker goals.

| Maven Option        | What Does _that thing_ Do?           |
| ------------- |:-------------:|
| dockerfile.skip | Disables the entire dockerfile plugin; all goals become no-ops. |
| dockerfile.build.skip | Disables the build goal; it becomes a no-op. |
| dockerfile.tag.skip | Disables the tag goal; it becomes a no-op. |
| dockerfile.push.skip | Disables the push goal; it becomes a no-op. |

For example to skip the entire dockerfile plugin:
```
mvn clean package -Ddockerfile.skip
```
