package org.apache.geode.gradle.test.dockerized

import net.rubygrapefruit.platform.ProcessLauncher
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

class DockerizedTestPlugin implements Plugin<Project> {
    String image
    String user
    Map volumes

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

        project.tasks.withType(Test).each { it -> configureTest(project, it) }
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
            println "DHE: ProcessLauncher is " + test.services.get(ProcessLauncher)
            // Inject our special dockerized process launcher
        }
    }
}
