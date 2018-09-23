# Usage

## Maven Goals

Goals available for this plugin:

| Goal | Description    | Default Phase |
| ---- | -------------- | ------------- |
| `dockerfile:build` | Builds a Docker image from a Dockerfile. | package |
| `dockerfile:tag` | Tags a Docker image. | package |
| `dockerfile:push` | Pushes a Docker image to a repository. | deploy |

### Skip Docker Goals Bound to Maven Phases

You can pass options to maven to disable the docker goals.

| Maven Option  | What Does it Do?           | Default Value |
| ------------- | -------------------------- | ------------- |
| `dockerfile.skip` | Disables the entire dockerfile plugin; all goals become no-ops. | false |
| `dockerfile.build.skip` | Disables the build goal; it becomes a no-op. | false |
| `dockerfile.tag.skip` | Disables the tag goal; it becomes a no-op. | false |
| `dockerfile.push.skip` | Disables the push goal; it becomes a no-op. | false |

For example, to skip the entire dockerfile plugin:
```
mvn clean package -Ddockerfile.skip
```

## Configuration

### Build Phase

| Maven Option  | What Does it Do?           | Required | Default Value |
| ------------- | -------------------------- | -------- | ------------- |
| `dockerfile.contextDirectory` | Directory containing the Dockerfile to build. | yes | none |
| `dockerfile.repository` | The repository to name the built image | no | none |
| `dockerfile.tag` | The tag to apply when building the Dockerfile, which is appended to the repository. | no | latest |
| `dockerfile.build.pullNewerImage` | Updates base images automatically. | no | true |
| `dockerfile.build.noCache` | Do not use cache when building the image. | no | false |
| `dockerfile.buildArgs` | Custom build arguments. | no | none |
