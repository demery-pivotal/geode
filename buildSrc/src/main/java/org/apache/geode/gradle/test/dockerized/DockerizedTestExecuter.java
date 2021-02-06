package org.apache.geode.gradle.test.dockerized;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.tasks.testing.detection.DefaultTestExecuter;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.time.Clock;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.process.internal.worker.WorkerProcessFactory;

public class DockerizedTestExecuter extends DefaultTestExecuter {
  public DockerizedTestExecuter(WorkerProcessFactory workerFactory, ActorFactory actorFactory,
      ModuleRegistry moduleRegistry, WorkerLeaseRegistry workerLeaseRegistry, int maxWorkerCount,
      Clock clock, DocumentationRegistry documentationRegistry, DefaultTestFilter testFilter) {
    super(new DockerizedWorkerProcessFactory(workerFactory), actorFactory, moduleRegistry, workerLeaseRegistry, maxWorkerCount, clock,
        documentationRegistry, testFilter);
  }

}
