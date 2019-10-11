# Changelog

## Upcoming release
- Fix progress output when using cacheFrom ([269][])

[269]: https://github.com/spotify/dockerfile-maven/pull/269

## 1.4.10 (released January 15 2019)
- Add support for --squash experimental build option ([248][])
- Add support for specifying a custom Dockerfile location ([89][])

[248]: https://github.com/spotify/dockerfile-maven/pull/248
[89]: https://github.com/spotify/dockerfile-maven/pull/89

## 1.4.9 (released October 25 2018)
- Upgrade docker-client dep from 8.14.2 to 8.14.3 to fix spotify/docker-client#1100

## 1.4.8 (released October 23 2018)
- Upgrade docker-client dep from 8.14.0 to 8.14.2
- Upgrade com.sparkjava:spark-core to fix CVE-2018-9159
- Improve documentation on referencing build artifacts

## 1.4.7 (released October 8 2018)
- Fix an ExceptionInInitializerError when plugin is used on Java 11 ([230][])
- change source/target version for compiler from 1.7 to 1.8. This means that
  the plugin will only run on Java 8 and above. ([231])

[230]: https://github.com/spotify/dockerfile-maven/pull/230
[231]: https://github.com/spotify/dockerfile-maven/pull/231

## 1.4.6 (released October 5 2018)

- Support for Java 9 and 10

## 1.3.6 (released September 13 2017)

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
