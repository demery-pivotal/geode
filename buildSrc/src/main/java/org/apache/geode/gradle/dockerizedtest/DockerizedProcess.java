package org.apache.geode.gradle.dockerizedtest;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.WaitResponse;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Represents a process executing in a Docker container.
 */
public class DockerizedProcess extends Process {
  private static final Logger LOGGER = Logging.getLogger(DockerizedProcess.class);

  private final DockerClient client;
  private final String containerId;
  private final PipedOutputStream stdinWrite = new PipedOutputStream();
  private final PipedInputStream stdoutRead = new PipedInputStream();
  private final PipedInputStream stderrRead = new PipedInputStream();
  private final PipedInputStream hostStdin = new PipedInputStream(stdinWrite);
  private final PipedOutputStream hostStdout = new PipedOutputStream(stdoutRead);
  private final PipedOutputStream hostStderr = new PipedOutputStream(stderrRead);
  private final AtomicInteger exitCode = new AtomicInteger();
  private final CountDownLatch finished = new CountDownLatch(1);

  /**
   * A callback to copy each frame of the container's output to this wrapper's stdout or stderr.
   */
  private final ResultCallback.Adapter<Frame> onOutputFromContainer = new ResultCallback.Adapter<Frame>() {
    @Override
    public void onNext(Frame frame) {
      StreamType streamType = frame.getStreamType();
      try {
        switch (streamType) {
          case STDOUT:
            hostStdout.write(frame.getPayload());
            break;
          case STDERR:
            hostStderr.write(frame.getPayload());
            break;
          default:
        }
      } catch (IOException e) {
        LOGGER.error("Error while writing to stream:", e);
      }
    }
  };

  /**
   * A callback to execute when the containerized process finishes. remember its exit code, stop watching its streams,
   * close this wrapper's output streams, and stop the container.
   */
  private final ResultCallback.Adapter<WaitResponse> onContainerizedProcessCompletion = new ResultCallback.Adapter<WaitResponse>() {
    @Override
    public void onNext(WaitResponse response) {
      exitCode.set(response.getStatusCode());
      try {
        onOutputFromContainer.close();
        onOutputFromContainer.awaitCompletion();
        hostStdout.close();
        hostStderr.close();
      } catch (Exception e) {
        LOGGER.debug("Error while detaching streams", e);
      } finally {
        removeContainer();
      }
    }
  };

  /**
   * Creates a DockerizedProcess that represents a process running in a Docker container.
   * @param client the Docker client that manages the container
   * @param containerId the ID of the container in which the process is running
   * @return a Process that represents the process in the container
   */
  public static Process attach(DockerClient client, String containerId) {
    try {
      DockerizedProcess process = new DockerizedProcess(client, containerId);
      process.attach();
      return process;
    } catch (Exception e) {
      throw new RuntimeException("Error while attaching to " + containerId + " in " + client, e);
    }
  }

  private DockerizedProcess(DockerClient client, String containerId) throws IOException {
    this.client = client;
    this.containerId = containerId;
  }

  @Override
  public OutputStream getOutputStream() {
    return stdinWrite;
  }

  @Override
  public InputStream getInputStream() {
    return stdoutRead;
  }

  @Override
  public InputStream getErrorStream() {
    return stderrRead;
  }

  @Override
  public int waitFor() throws InterruptedException {
    finished.await();
    return exitCode.get();
  }

  @Override
  public int exitValue() {
    if (finished.getCount() != 0) {
      throw new IllegalThreadStateException("Process in " + this + " is still running");
    }
    return exitCode.get();
  }

  @Override
  public void destroy() {
    client.killContainerCmd(containerId).exec();
  }

  @Override
  public String toString() {
    return "docker container " + containerId + " on " + client;
  }

  /**
   * Attach this wrapper's streams to the container's, and set a callback for when the
   * containerized process finishes.
   * @throws Exception if an error occurs while attaching to the container
   */
  private void attach() throws Exception {
    client.attachContainerCmd(containerId)
        .withFollowStream(true)
        .withStdOut(true)
        .withStdErr(true)
        .withStdIn(hostStdin)
        .exec(onOutputFromContainer);
    if (!onOutputFromContainer.awaitStarted(10, SECONDS)) {
      String message = "Did not attach to container " + containerId + " within 10 seconds";
      LOGGER.warn(message);
      throw new RuntimeException(message);
    }
    client.waitContainerCmd(containerId)
        .exec(onContainerizedProcessCompletion);
  }

  private void removeContainer() {
    try {
      client.removeContainerCmd(containerId).exec();
    } catch (Exception e) {
      LOGGER.debug("Error while removing container", e);
    }
  }
}
