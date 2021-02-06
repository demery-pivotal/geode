package org.apache.geode.gradle.test.dockerized

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.netty.NettyDockerCmdExecFactory

class DockerizedTestConfig {
    @Deprecated // Use prepareJavaCommand
    Closure beforeContainerCreate
    String user;
    String image;
    Map<String, String> volumes = new HashMap<>();
    DockerClient client = DockerClientBuilder.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder())
            .withDockerCmdExecFactory(new NettyDockerCmdExecFactory())
            .build()

    /**
     * Prepares the process builder before executing the process in docker container.
     */
    Closure<ProcessBuilder> prepareJavaCommand = { it };
}
