# Changelog

## 1.3.6 (unreleased)

- Add support for using maven settings.xml file to provide docker authorization ([65][])

[65]: https://github.com/spotify/dockerfile-maven/pull/65

## 1.3.3 (released July 11 2017)

- Add support for supplying build-args (`ARG` in Dockerfile) in pom.xml with
  `<buildArgs>` [41][]

- Allow disabling of Google Container Registry credential checks with
  `-Ddockerfile.googleContainerRegistryEnabled` or
  `<googleContainerRegistryEnabled>false</googleContainerRegistryEnabled>`([43][])


[41]: https://github.com/spotify/dockerfile-maven/pull/41
[43]: https://github.com/spotify/dockerfile-maven/pull/43


## 1.3.2 (released July 10 2017)

- Upgrade to docker-client 8.8.0 ([38][])

- Improved fix for NullPointerException in LoggingProgressHandler ([36][])

[36]: https://github.com/spotify/dockerfile-maven/pull/36
[38]: https://github.com/spotify/dockerfile-maven/pull/38


## 1.3.1 (released June 30 2017)

- Fix NullPointerException in LoggingProgressHandler ([30][])

[30]: https://github.com/spotify/dockerfile-maven/pull/30


## 1.3.0 (released June 5 2017)

- Support for authentication to Google Container Registry ([13][], [17][])

[13]: https://github.com/spotify/dockerfile-maven/pull/13
[17]: https://github.com/spotify/dockerfile-maven/pull/17

## Earlier releases 

Please check the [list of commits on Github][commits].

[commits]: https://github.com/spotify/dockerfile-maven/commits/master
