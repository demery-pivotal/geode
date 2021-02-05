package org.apache.geode.gradle.test.dockerized

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.service.ServiceRegistry

class DockerizedTestPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        println "DHE: Applying to $project.path with start parameter $project.gradle.startParameter"
        project.tasks.withType(Test).each { it -> configureTest(project, it) }
        project.tasks.whenTaskAdded { task ->
            if (task instanceof Test) configureTest(project, task)
        }
        println "DHE: Done applying to $project.path"
    }

    void configureTest(project, test) {
        if (!project.hasProperty('dunitDockerImage')) {
            println "DHE: Not configuring $test.path because no docker image"
            return
        }
        println "DHE: Configuring $test.path"
        def config = test.extensions.create("docker", DockerizedTestConfig)
        config.image = project.dunitDockerImage
        config.user = project.dunitDockerUser
        def gradleHome = System.getenv('GRADLE_USER_HOME') ?: "${System.getenv('HOME')}/.gradle"
        config.volumes = ["${gradleHome}":gradleHome]
        if (project.hasProperty('dunitDockerVolumes')) {
            config.volumes << project.dunitDockerVolumes
        }

        println "DHE: Done configuring $test.path with config $config"
    }
}
