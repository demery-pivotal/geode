package org.apache.geode.gradle.test.dockerized;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.ProcessLauncher;

/**
 * Launches a Gradle sub-process in a Docker container.
 */
public class DockerizedProcessLauncher implements ProcessLauncher {
  private final DockerizedTestConfig config;
  private final DockerClient client;

  public DockerizedProcessLauncher(DockerizedTestConfig config, DockerClient client) {
    this.config = config;
    this.client = client;
  }

  /**
   * Launches a process in a Docker container.
   * @param processBuilder a builder that describes the process to launch
   * @return a Process that represents the process running in the Docker container
   * @throws NativeException
   */
  @Override
  public Process start(ProcessBuilder processBuilder) throws NativeException {
    config.getPrepareJavaCommand().call(processBuilder);
    CreateContainerCmd createContainer = client.createContainerCmd(config.getImage())
        .withTty(false)
        .withStdinOpen(true)
        .withWorkingDir(processBuilder.directory().getAbsolutePath())
        .withEnv(asStrings(processBuilder.environment()))
        .withCmd(processBuilder.command());
    setUser(createContainer);
    setVolumes(createContainer);

    String containerId = createContainer.exec().getId();
    client.startContainerCmd(containerId).exec();
    if (!isRunning(containerId)) {
      throw new RuntimeException("Container " + containerId + " is not running!");
    }

    return DockerizedProcess.attach(client, containerId);
  }

  private boolean isRunning(String containerId) {
    Boolean isRunning = client.inspectContainerCmd(containerId).exec()
        .getState()
        .getRunning();
    return isRunning != null && isRunning;
  }

  private void setUser(CreateContainerCmd command) {
    String user = config.getUser();
    if (user != null) {
      command.withUser(user);
    }
  }

  private void setVolumes(CreateContainerCmd command) {
    List<Bind> binds = config.getVolumes().entrySet().stream()
        .map(e -> new Bind(e.getKey(), new Volume(e.getValue())))
        .collect(toList());
    List<Volume> volumes = binds.stream()
        .map(Bind::getVolume)
        .collect(toList());
    command.withVolumes(volumes);
    command.getHostConfig().withBinds(binds);
  }

  private static List<String> asStrings(Map<String, String> map) {
    return map.entrySet().stream()
        .map(e -> e.getKey() + "=" + e.getValue())
        .collect(toList());
  }
}
