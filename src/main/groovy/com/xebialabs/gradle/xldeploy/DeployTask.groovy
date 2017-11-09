/*
 * Copyright 2017 XEBIALABS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.xebialabs.gradle.xldeploy

import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.base.Strings
import com.google.common.collect.Lists
import com.xebialabs.deployit.engine.api.DeploymentService
import com.xebialabs.deployit.engine.api.RepositoryService
import com.xebialabs.deployit.engine.api.dto.Deployment
import com.xebialabs.deployit.engine.api.execution.TaskExecutionState
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem
import com.xebialabs.deployit.plugin.api.validation.ValidationMessage
import org.gradle.api.GradleException
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.TaskAction

/**
 * This task uploads a DAR package to XL Deploy and can run a deployment
 * of it to an environment.
 */
class DeployTask extends BaseDeploymentTask {

  private Deployment generatedDeployment
  private String currentVersion

  @TaskAction
  @SuppressWarnings("GroovyUnusedDeclaration")
  public void executeDeployment() {
    try {
      if(xldExtension.xldDeployLogLevel) {
        logLevel = LogLevel.valueOf(xldExtension.xldDeployLogLevel)
      }
      else {
        logLevel = LogLevel.INFO
      }
      boot()
      final ConfigurationItem deploymentPackage = uploadPackage();
      environmentId = environmentId ?: xldExtension.xldEnvironmentId
      if (Strings.emptyToNull(environmentId) != null) {
        deployPackage(deploymentPackage)
      } else {
        logger.log(logLevel, "No environmentId is specified for $XlDeployPlugin.DEPLOY_TASK_NAME so skipping deployment")
      }
    } finally {
      shutdown()
    }
  }

  private ConfigurationItem uploadPackage() {

    def darFiles = project.configurations.getByName(DarConfigurationTask.DAR_CONFIGURATION_NAME).getAllArtifacts()
    if (darFiles.isEmpty()) {
      throw new GradleException("Could not find any generated DAR packages in the project. Did the 'dar' task run?", null)
    }
    if (darFiles.size() > 1) {
      throw new GradleException("Found more than one DAR packages generated in project ${project.name}: $darFiles.", null)
    }
    def darFile = darFiles.files.getSingleFile()

    final ConfigurationItem deploymentPackage = getDeploymentHelper().uploadPackage(darFile);
    logger.log(logLevel, "Application [${deploymentPackage.getId()}] has been imported");
    return deploymentPackage;
  }

  private void deployPackage(ConfigurationItem deploymentPackage) {
    final ConfigurationItem targetEnvironment = getTargetEnvironment();

    Boolean update = getDeploymentHelper().isApplicationDeployed(deploymentPackage.getId(), targetEnvironment.getId());
    generateDeployment(deploymentPackage, targetEnvironment, update);

    logger.log(logLevel, "Deployeds to be included into generated deployment:");
    for (ConfigurationItem d : generatedDeployment.getDeployeds()) {
      logger.log(logLevel, "    -> " + d.getId());
    }

    if (!Strings.isNullOrEmpty(orchestrator)) {
      logger.log(logLevel, "Using orchestrator: " + orchestrator);
      generatedDeployment.getDeployedApplication().setProperty("orchestrator", orchestrator);
    }

    String taskId = generateDeploymentTask();
    if (testMode) {
      logger.log(logLevel, " ... Test mode discovered => displaying and cancelling the generated task");
      getDeploymentHelper().logTaskState(logLevel, taskId);
      communicator.getProxies().getTaskService().cancel(taskId);
      return;
    }
    runDeploymentTask(taskId);

    if (this.deletePreviouslyDeployedDar && update && currentVersion != null) {
      logger.lifecycle("Removing the previous version $currentVersion");
      try {
        communicator.getProxies().getRepositoryService().delete(currentVersion);
      } catch (Exception e) {
        logger.error("Cannot delete $currentVersion: ${e.getMessage()}", e);
      }
    }
  }

  private ConfigurationItem getTargetEnvironment() {
    if (Strings.emptyToNull(environmentId) == null) {
      throw new ProjectConfigurationException("Mandatory parameter environmentId is not set for task ${XlDeployPlugin.DEPLOY_TASK_NAME}", null);
    }

    ConfigurationItem targetEnvironment = getDeploymentHelper().readCiOrNull(environmentId);
    if (targetEnvironment == null) {
      throw new ProjectConfigurationException("Could not find environment [$environmentId]", null);
    }
    getDeploymentHelper().logEnvironment(targetEnvironment);
    return targetEnvironment;
  }

  private void runDeploymentTask(String taskId) {
    if (skipMode) {
      logger.log(logLevel, " ... Skip mode discovered");
      getDeploymentHelper().skipAllSteps(taskId);
    }

    String detailedHelpMessage = ""
    if (logLevel == LogLevel.INFO) {
      detailedHelpMessage = " (run Gradle with '-i' to see detailed output)"
    }
    logger.lifecycle("Executing generated deployment task: $taskId$detailedHelpMessage");
    try {
      TaskExecutionState taskExecutionState = getDeploymentHelper().executeAndArchiveTask(logLevel, taskId);
      if (taskExecutionState.isExecutionHalted()) {
        throw new GradleException("Errors when executing task $taskId$detailedHelpMessage", null);
      }
    } catch (IllegalStateException e) {
      if (cancelTaskOnError) {
        logger.lifecycle("Cancel task on error " + taskId);
        communicator.getProxies().getTaskService().cancel(taskId);
      }
      throw e;
    }
  }

  private String generateDeploymentTask() {
    String taskId;
    logger.log(logLevel, "Creating a task");
    try {
      taskId = communicator.getProxies().getDeploymentService()
          .createTask(getDeploymentHelper().validateDeployment(generatedDeployment));
    } catch (DeploymentHelper.DeploymentValidationError validationError) {
      for (ValidationMessage validationMessage : validationError.getValidationMessages()) {
        logger.error(validationMessage.toString());
      }
      throw new RuntimeException(validationError);
    }
    logger.log(logLevel, "    -> task id: $taskId");
    return taskId;
  }

  private void generateDeployment(ConfigurationItem deploymentPackage, ConfigurationItem targetEnvironment, Boolean update) {
    DeploymentService deploymentService = communicator.getProxies().getDeploymentService();
    RepositoryService repositoryService = communicator.getProxies().getRepositoryService();

    if (update) {
      logger.lifecycle("Application already exists => preparing update");
      generatedDeployment = deploymentService.prepareUpdate(deploymentPackage.getId(),
          getDeployedApplicationId(deploymentPackage.getId(), targetEnvironment.getId()));
      currentVersion = repositoryService.read(getDeployedApplicationId(
          deploymentPackage.getId(), targetEnvironment.getId())).getProperty("version");
      if (generateDeployedOnUpgrade) {
        generatedDeployment = deploymentService.prepareAutoDeployeds(generatedDeployment);
      }
    } else {
      logger.lifecycle("Application not found in deployed => preparing for initial deployment");
      generatedDeployment = deploymentService.prepareInitial(deploymentPackage.getId(), targetEnvironment.getId());
      generatedDeployment = deploymentService.prepareAutoDeployeds(generatedDeployment);
    }
  }

  private static String getDeployedApplicationId(String source, String target) {
    // source = 'Applications/tomcatApps/deployit-tomcat/1.0-20120522-173607'
    // target = "Environments/DefaultEnvironment"
    // return "Environments/DefaultEnvironment/deployit-tomcat"
    List<String> splitSource =  Lists.newArrayList(Splitter.on("/").split(source));
    final String appName = splitSource.get(splitSource.size() - 2);
    return Joiner.on("/").join(target, appName);
  }

}
