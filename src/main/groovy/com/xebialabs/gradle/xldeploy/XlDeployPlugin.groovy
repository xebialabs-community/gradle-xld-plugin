/*
 * Copyright 2018 XEBIALABS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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

    DarConfigurationTask configureDar = project.tasks.create("configureDar", DarConfigurationTask.class)
    configureDar.configure {
      group = BasePlugin.BUILD_GROUP
      description = "Create a DAR package"
      darTask = dar
    }

    dar.configure {
      dependsOn configureDar
    }

    ArchivePublishArtifact darArtifact = new ArchivePublishArtifact(dar)
    project.configurations.create(DarConfigurationTask.DAR_CONFIGURATION_NAME).artifacts.add(darArtifact)

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
      if (p.hasProperty('xldEnvironmentId')) {
        ext.xldEnvironmentId = ext.xldEnvironmentId ?: p.properties.get('xldEnvironmentId')
      }
    }
  }
}
