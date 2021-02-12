package org.apache.geode.gradle.test.dockerized;

import org.gradle.api.Action;
import org.gradle.process.internal.worker.MultiRequestWorkerProcessBuilder;
import org.gradle.process.internal.worker.RequestHandler;
import org.gradle.process.internal.worker.SingleRequestWorkerProcessBuilder;
import org.gradle.process.internal.worker.WorkerProcessBuilder;
import org.gradle.process.internal.worker.WorkerProcessContext;
import org.gradle.process.internal.worker.WorkerProcessFactory;

public class DockerizedWorkerProcessFactory implements WorkerProcessFactory {
  private final WorkerProcessFactory delegate;

  public DockerizedWorkerProcessFactory(WorkerProcessFactory delegate) {
    this.delegate = delegate;
  }

  @Override
  public WorkerProcessBuilder create(Action<? super WorkerProcessContext> action) {
    WorkerProcessBuilder builder = delegate.create(action);
    System.out.printf("DHE: %s created %s%n", this, builder);
    return builder;
  }

  @Override
  public <IN, OUT> SingleRequestWorkerProcessBuilder<IN, OUT> singleRequestWorker(
      Class<? extends RequestHandler<? super IN, ? extends OUT>> aClass) {
    return delegate.singleRequestWorker(aClass);
  }

  @Override
  public <IN, OUT> MultiRequestWorkerProcessBuilder<IN, OUT> multiRequestWorker(
      Class<? extends RequestHandler<? super IN, ? extends OUT>> aClass) {
    return delegate.multiRequestWorker(aClass);
  }
}
