/*
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.gradle.xldeploy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.plugins.BasePlugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class XlDeployPlugin implements Plugin<Project> {

  private static final Logger log = LoggerFactory.getLogger(XlDeployPlugin.class)

  public static final String DAR_TASK_NAME = "dar"
  public static final String DEPLOY_TASK_NAME = "deploy"

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
    project.configurations.create(DarTask.DAR_CONFIGURATION_NAME).artifacts.add(darArtifact)

    DeployTask deploy = project.tasks.create(DEPLOY_TASK_NAME, DeployTask.class)
    deploy.configure {
      group = BasePlugin.UPLOAD_GROUP
      description = "Upload DAR package to XL Deploy and run a deployment to an environment"
      dependsOn dar
    }
  }

  private static def addExtensions(Project p) {
    def ext = p.extensions.create(PLUGIN_EXTENSION_NAME, XlDeployPluginExtension.class).with {
      project = p
      manifest = p.file("${project.projectDir}/src/main/dar/deployit-manifest.xml")

      xldUrl = "http://localhost:4516/"
      it
    }
    p.afterEvaluate {
      ext.xldUsername = ext.xldUsername ?: p.properties.get('xldUsername')
      ext.xldPassword = ext.xldPassword ?: p.properties.get('xldPassword')
    }
  }

}
