/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedjak.gradle.plugins.dockerizedtest

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import org.apache.commons.lang3.SystemUtils
import org.apache.maven.artifact.versioning.ComparableVersion
import org.gradle.StartParameter
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.DefaultFileCollectionFactory
import org.gradle.api.internal.tasks.testing.detection.DefaultTestExecuter
import org.gradle.api.tasks.testing.Test
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.remote.MessagingServer
import org.gradle.internal.time.Clock
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.process.internal.JavaExecHandleFactory
import org.gradle.process.internal.worker.DefaultWorkerProcessFactory

import javax.inject.Inject

/**
 * DHE:
 * - Configures each test task to run each test worker in a separate Docker container.
 */
class DockerizedTestPlugin implements Plugin<Project> {
    // DHE: minimum supported Gradle version. Perhaps we should also constrain the maximum version.
    def supportedVersion = '6.8'
    def currentUser
    def messagingServer
    // DHE: What is the purpose of this?
    def memoryManager = new NoMemoryManager()

    @Inject
    DockerizedTestPlugin(MessagingServer messagingServer) {
        this.currentUser = SystemUtils.IS_OS_WINDOWS ? "0" : "id -u".execute().text.trim()
        this.messagingServer = new DockerizedMessagingServer(messagingServer.connector, messagingServer.executorFactory)
    }

    void configureTest(project, test) {
        def ext = test.extensions.create("docker", DockerizedTestExtension, [] as Object[])
        def startParameter = project.gradle.startParameter
        ext.volumes = ["$startParameter.gradleUserHomeDir": "$startParameter.gradleUserHomeDir",
                       "$project.projectDir"              : "$project.projectDir"]
        ext.user = currentUser
        test.doFirst {
            def extension = test.extensions.docker

            if (extension?.image) {
                def processBuilderFactory = newProcessBuilderFactory(project, extension, test.processBuilderFactory)
                test.testExecuter = new DefaultTestExecuter(
                        processBuilderFactory,
                        actorFactory,
                        moduleRegistry,
                        services.get(WorkerLeaseRegistry),
                        services.get(BuildOperationExecutor),
                        getServices().get(StartParameter).getMaxWorkerCount(),
                        services.get(Clock),
                        services.get(DocumentationRegistry),
                        getFilter());

                // DHE: Launch a docker client if it isn't already assigned
                if (!extension.client) {
                    // DHE: If we give each test task its own client, would the client already be
                    // assigned at this point?
                    extension.client = createDefaultClient()
                }
            }
        }
    }

    DockerClient createDefaultClient() {
        DockerClientBuilder.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder())
                .withDockerCmdExecFactory(new NettyDockerCmdExecFactory())
                .build()
    }

    void apply(Project project) {

        boolean unsupportedVersion = new ComparableVersion(project.gradle.gradleVersion).compareTo(new ComparableVersion(supportedVersion)) < 0
        if (unsupportedVersion) throw new GradleException("dockerized-test plugin requires Gradle ${supportedVersion}+")

        // DHE: Configure all existing test tasks
        project.tasks.withType(Test).each { test -> configureTest(project, test) }

        // DHE: Arrange to configure subsequently added test tasks
        project.tasks.whenTaskAdded { task ->
            if (task instanceof Test) configureTest(project, task)
        }
    }

    def newProcessBuilderFactory(project, extension, defaultProcessBuilderFactory) {

        def executorFactory = new DefaultExecutorFactory()
        def executor = executorFactory.create("Docker container link")
        def buildCancellationToken = new DefaultBuildCancellationToken()

        def defaultfilecollectionFactory = new DefaultFileCollectionFactory(project.fileResolver, null)

        // DHE: Create an exec handle factory that creates Dockerized exec handles
        def execHandleFactory = [newJavaExec: { ->
            new DockerizedJavaExecHandleBuilder(extension, project.fileResolver,
                    defaultfilecollectionFactory, executor, buildCancellationToken)
        }] as JavaExecHandleFactory

        // DHE: Return a default worker process factory that gets most details from the given
        // factory, but uses our custom exec handle factory, messaging server, and memory manager.
        new DefaultWorkerProcessFactory(defaultProcessBuilderFactory.loggingManager,
                messagingServer,
                defaultProcessBuilderFactory.workerImplementationFactory.classPathRegistry,
                defaultProcessBuilderFactory.idGenerator,
                defaultProcessBuilderFactory.gradleUserHomeDir,
                defaultProcessBuilderFactory.workerImplementationFactory.temporaryFileProvider,
                execHandleFactory,
                defaultProcessBuilderFactory.workerImplementationFactory.jvmVersionDetector,
                defaultProcessBuilderFactory.outputEventListener,
                memoryManager
        )
    }
}
