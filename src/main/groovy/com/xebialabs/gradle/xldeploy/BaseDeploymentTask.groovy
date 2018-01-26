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

import com.xebialabs.deployit.booter.remote.BooterConfig
import com.xebialabs.deployit.booter.remote.DeployitCommunicator
import com.xebialabs.deployit.booter.remote.RemoteBooter
import org.gradle.api.DefaultTask
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.logging.LogLevel

import static XlDeployPlugin.PLUGIN_EXTENSION_NAME

abstract class BaseDeploymentTask extends DefaultTask {

  protected XlDeployPluginExtension xldExtension = project.extensions.getByName(
      PLUGIN_EXTENSION_NAME) as XlDeployPluginExtension

  /**
   * Activate the skip mode: generate the plan, skip all the steps, validate the task.
   * <pre>false</pre> by default.
   */
  boolean skipMode;

  /**
   * Set the orchestrator used during the deployment.
   */
  String orchestrator;

  /**
   * Activate the test mode, generate the plan, display all the steps, validate the task
   */
  boolean testMode;

  /**
   * Id of the environment used for the deployment.
   */
  String environmentId;

  /**
   * Delete the previous deployed dar. Useful if you work with the SNAPSHOT versions you don't want to keep in your repository.
   */
  boolean deletePreviouslyDeployedDar;

  /**
   * Flag controlling whether, during the upgrade operation, the Deployed objects should generated (like an initial deployment) or reused.
   * For security reasons, the default value is false but should be set to true to apply the modifications (new Ear, removed links) even during upgrade.
   */
  boolean generateDeployedOnUpgrade;

  /**
   * When a task falls in error, it is cancelled. Sometime it could be interesting to debug it using the UI or the CLI. When this flag is set to false, the
   * task will be left as is.
   */
  boolean cancelTaskOnError;

  /**
   * Set a logging level for deployment messages, defaults to INFO if not set
   */
  LogLevel logLevel;

  protected DeployitCommunicator communicator;

  private DeploymentHelper deploymentHelper;

  /**
   * Boots REST API
   */
  protected void boot() {
    try {
      URL url = new URL(xldExtension.xldUrl)

      BooterConfig config = BooterConfig.builder()
          .withProtocol(BooterConfig.Protocol.valueOf(url.getProtocol().toUpperCase()))
          .withCredentials(
          failIfEmpty(xldExtension.xldUsername, 'xldUsername'),
          failIfEmpty(xldExtension.xldPassword, 'xldPassword'))
          .withHost(url.getHost())
          .withPort(url.getPort() != -1 ? url.getPort() : url.getDefaultPort())
          .withContext(url.getPath())
          .withSocketTimeout(xldExtension.socketTimeout)
          .build();

      communicator = RemoteBooter.boot(config);

    } catch (MalformedURLException e) {
      throw new ProjectConfigurationException("Could not parse URL '${xldExtension.xldUrl}'", e)
    }
  }

  protected void shutdown() {
    if (communicator) {
      communicator.shutdown();
    }
  }

  protected DeploymentHelper getDeploymentHelper() {
    if (deploymentHelper == null) {
      deploymentHelper = new DeploymentHelper(logger, communicator);
    }
    return deploymentHelper;
  }

  private static String failIfEmpty(String value, String propertyName) {
    if (value == null) {
      throw new ProjectConfigurationException("Required property '$propertyName' is not configured. " +
          "Please specify it in '${PLUGIN_EXTENSION_NAME} { ... }' block of your build.gradle file " +
          "or in ~/.gradle/gradle.properties file", null)
    }
    value
  }

}
