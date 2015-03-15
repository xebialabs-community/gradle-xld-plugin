/*
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
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
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.tasks.TaskAction
import org.gradle.mvn3.org.apache.maven.ProjectBuildFailureException

import static XlDeployPlugin.DEPLOY_TASK_NAME

/**
 * This task uploads a DAR package to XL Deploy and can run a deployment
 * of it to an environment.
 */
class DeployTask extends BaseDeploymentTask {

  private Deployment generatedDeployment
  private String currentVersion

  @TaskAction
  public void executeDeployment() {
    try {
      boot()
      final ConfigurationItem deploymentPackage = uploadPackage();
      environmentId = environmentId ?: xldExtension.xldEnvironmentId
      if (Strings.emptyToNull(environmentId) != null) {
        deployPackage(deploymentPackage)
      } else {
        logger.debug("No environmentId is specified for $DEPLOY_TASK_NAME so skipping deployment")
      }
    } finally {
      shutdown()
    }
  }

  private ConfigurationItem uploadPackage() {

    def darFiles = project.configurations.getByName(DarTask.DAR_CONFIGURATION_NAME).getAllArtifacts()
    if (darFiles.isEmpty()) {
      throw new ProjectBuildFailureException("Could not find any generated DAR packages in the project. Did the 'dar' task run?", null)
    }
    if (darFiles.size() > 1) {
      throw new ProjectBuildFailureException("Found more than one DAR packages generated in project ${project.name}: $darFiles.", null)
    }
    def darFile = darFiles.files.getSingleFile()

    final ConfigurationItem deploymentPackage = getDeploymentHelper().uploadPackage(darFile);
    logger.info("Application [${deploymentPackage.getId()}] has been imported");
    return deploymentPackage;
  }

  private void deployPackage(ConfigurationItem deploymentPackage) {
    final ConfigurationItem targetEnvironment = getTargetEnvironment();

    Boolean update = getDeploymentHelper().isApplicationDeployed(deploymentPackage.getId(), targetEnvironment.getId());
    generateDeployment(deploymentPackage, targetEnvironment, update);

    logger.info("Deployeds to be included into generatedDeployment:");
    for (ConfigurationItem d : generatedDeployment.getDeployeds()) {
      logger.info("    -> " + d.getId());
    }

    if (!Strings.isNullOrEmpty(orchestrator)) {
      logger.info("Using orchestrator: " + orchestrator);
      generatedDeployment.getDeployedApplication().setProperty("orchestrator", orchestrator);
    }

    String taskId = generateDeploymentTask();
    if (testMode) {
      logger.info(" ... Test mode discovered => displaying and cancelling the generated task");
      getDeploymentHelper().logTaskState(taskId);
      communicator.getProxies().getTaskService().cancel(taskId);
      return;
    }
    runDeploymentTask(taskId);

    if (this.deletePreviouslyDeployedDar && update && currentVersion != null) {
      logger.info("removing the previous version $currentVersion");
      try {
        communicator.getProxies().getRepositoryService().delete(currentVersion);
      } catch (Exception e) {
        logger.error("Cannot delete $currentVersion: ${e.getMessage()}", e);
      }
    }
  }

  private ConfigurationItem getTargetEnvironment() {
    if (Strings.emptyToNull(environmentId) == null) {
      throw new ProjectConfigurationException("Mandatory parameter environmentId is not set for task ${DEPLOY_TASK_NAME}", null);
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
      logger.info(" ... Skip mode discovered");
      getDeploymentHelper().skipAllSteps(taskId);
    }

    logger.info("Executing generatedDeployment task");
    try {
      TaskExecutionState taskExecutionState = getDeploymentHelper().executeAndArchiveTask(taskId);
      if (taskExecutionState.isExecutionHalted()) {
        throw new ProjectBuildFailureException("Errors when executing task $taskId. Please run with '-i' for more information", null);
      }
    } catch (IllegalStateException e) {
      if (cancelTaskOnError) {
        logger.info("cancel task on error " + taskId);
        communicator.getProxies().getTaskService().cancel(taskId);
      }
      throw e;
    }
  }

  private String generateDeploymentTask() {
    String taskId;
    logger.info("Creating a task");
    try {
      taskId = communicator.getProxies().getDeploymentService()
          .createTask(getDeploymentHelper().validateDeployment(generatedDeployment));
    } catch (DeploymentHelper.DeploymentValidationError validationError) {
      for (ValidationMessage validationMessage : validationError.getValidationMessages()) {
        logger.error(validationMessage.toString());
      }
      throw new RuntimeException(validationError);
    }
    logger.info("    -> task id: $taskId");
    return taskId;
  }

  private void generateDeployment(ConfigurationItem deploymentPackage, ConfigurationItem targetEnvironment, Boolean update) {
    DeploymentService deploymentService = communicator.getProxies().getDeploymentService();
    RepositoryService repositoryService = communicator.getProxies().getRepositoryService();

    if (update) {
      logger.info(" ... Application already exists => preparing update");
      generatedDeployment = deploymentService.prepareUpdate(deploymentPackage.getId(),
          getDeployedApplicationId(deploymentPackage.getId(), targetEnvironment.getId()));
      currentVersion = repositoryService.read(getDeployedApplicationId(
          deploymentPackage.getId(), targetEnvironment.getId())).getProperty("version");
      if (generateDeployedOnUpgrade) {
        generatedDeployment = deploymentService.prepareAutoDeployeds(generatedDeployment);
      }
    } else {
      logger.info(" ... Application not found in deployed => preparing for initial deployment");
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
