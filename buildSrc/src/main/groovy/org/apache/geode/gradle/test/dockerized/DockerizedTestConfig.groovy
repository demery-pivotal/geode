package org.apache.geode.gradle.test.dockerized

class DockerizedTestConfig {
    Closure beforeContainerCreate
    String user;
    String image;
    Map<String, String> volumes = new HashMap<>();

    /**
     * Prepares the process builder before executing the process in docker container.
     */
    Closure<ProcessBuilder> prepareJavaCommand = { it };
}
