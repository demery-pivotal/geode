package org.apache.geode.gradle.test.dockerized


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.process.internal.worker.WorkerProcessFactory

class DockerizedTestPlugin implements Plugin<Project> {
    String image
    String user
    Map volumes
    WorkerProcessFactory dockerWorkerProcessFactory

    @Override
    void apply(Project project) {
        if (!project.hasProperty('dunitDockerImage')) {
            return
        }
        image = project.dunitDockerImage
        user = project.dunitDockerUser
        def startParameter = project.gradle.startParameter
        def gradleHome = System.getenv('GRADLE_USER_HOME') ?: "${System.getenv('HOME')}/.gradle"
        def gradleUserHomeDir = startParameter.gradleUserHomeDir.getAbsolutePath()
        def projectDir = project.projectDir.getAbsolutePath()
        volumes = [gradleHome       : gradleHome,
                   gradleUserHomeDir: gradleUserHomeDir,
                   projectDir       : projectDir]
        if (project.hasProperty('dunitDockerVolumes')) {
            volumes << project.dunitDockerVolumes
        }

        def existingTestTasks = project.tasks.withType(Test)
        dockerWorkerProcessFactory = existingTestTasks.first().processBuilderFactory
        println "DHE: $this docker worker process factory is $dockerWorkerProcessFactory"
        existingTestTasks.each {
            configureTest(project, it)
        }
        project.tasks.whenTaskAdded { task ->
            if (task instanceof Test) configureTest(project, task)
        }
    }

    void configureTest(project, test) {
        def config = test.extensions.create("docker", DockerizedTestConfig)
        config.image = image
        config.user = user
        config.volumes = volumes
        test.doFirst {
        }
    }
}
