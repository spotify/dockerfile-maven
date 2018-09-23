# Authentication

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

Since version 1.4.3, using an encrypted password in the Maven settings file is supported.  For more
information about encrypting server passwords in `settings.xml`,
[read the documentation here](https://maven.apache.org/guides/mini/guide-encryption.html).

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

