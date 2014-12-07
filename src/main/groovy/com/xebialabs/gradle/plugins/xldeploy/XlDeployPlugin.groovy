package com.xebialabs.gradle.plugins.xldeploy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.plugins.BasePlugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class XlDeployPlugin implements Plugin<Project> {

  private static final Logger log = LoggerFactory.getLogger(XlDeployPlugin.class)

  public static final String DAR_TASK_NAME = "dar"
  public static final String DAR_CONFIGURATION_NAME = "dar"

  public static final String PLUGIN_EXTENSION_NAME = "xldeploy"

  @Override
  void apply(Project project) {

    log.info("Configuring XL Deploy plugin on project ${project.name}")

    addExtensions(project)

    DarTask dar = project.tasks.create(DAR_TASK_NAME, DarTask.class)
    dar.configure {
      group = BasePlugin.BUILD_GROUP
      description = "Create a DAR package"
    }

    ArchivePublishArtifact darArtifact = new ArchivePublishArtifact(dar)
    project.configurations.create(DAR_CONFIGURATION_NAME).artifacts.add(darArtifact)
  }

  static def addExtensions(Project p) {
    p.extensions.create(PLUGIN_EXTENSION_NAME, XlDeployPluginExtension.class).with {
      project = p
      manifest = p.file("${project.projectDir}/src/main/dar/deployit-manifest.xml")
      it
    }
  }

}
