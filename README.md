# Gradle XL Deploy Plugin

This is a [Gradle](http://gradle.org) plugin which allows you to deploy your application to an environment as a part of the build process. It uses [XL Deploy](http://xebialabs.com/products/xl-deploy/) server to perform the deployment.

This plugin can be handy to easily and often deploy your application to a development environment. Another way to use it would be as part of Gradle release process, so that final non-SNAPSHOT artifacts can be automatically put to your acceptance or production environment (probably for small projects). If you have a CI server, you might use corresponding XL Deploy plugin instead to do deployments, see for example [Jenkins plugin](https://wiki.jenkins-ci.org/display/JENKINS/XL+Deploy+Plugin).

An overview of deployment process is following:

1. An environment is configured in XL Deploy: for example a remote Linux host with a Tomcat container. Please consult the [XL Deploy documentation](https://docs.xebialabs.com/xl-deploy/latest/referencemanual.html) for details about how to do that.
2. User defines _deployables_ for his/her application: a list of artifacts that the application consists of. A simple example is a single WAR file. Deployables are defined in a `deployit-manifest.xml` file in the project (more about it below).
3. Gradle plugin creates a _DAR_ package - a ZIP file containing the application to be deployed, understandable by XL Deploy.
4. Gradle plugin uploads the package to XL Deploy and starts a deployment to the environment.
5. XL Deploy performs the deployment.
6. Your application is live!

Environment and deployables configuration needs to be done once, and then the whole process runs automatically.

# Installation

The plugin is available at [Bintray's JCenter repository](https://bintray.com/bintray/jcenter) and [Gradle Plugins repository](http://plugins.gradle.org/), with some dependencies available in public XebiaLabs Maven repository. You can add the plugin to your `build.gradle` using following code snippet:

    buildscript {
      repositories {
        jcenter()
        maven {
          url "http://www.knopflerfish.org/maven2/"
        }
        maven {
          url "https://dist.xebialabs.com/public/maven2/"
        }
      }
      dependencies {
        classpath 'com.xebialabs.gradle:xl-deploy-gradle-plugin:0.2.0'
      }
    }

    apply plugin: 'com.xebialabs.xl-deploy'

You also need a running instance of XL Deploy server to use this Gradle plugin. If you never worked with XL Deploy before you can download a community edition from the [XebiaLabs website](http://xebialabs.com/download/xl-deploy/) and install it on your local machine.

# Usage

This plugin adds two tasks to the project: `dar` and `deploy`.

## `dar` task

`dar` task packages your application in a format understandable by XL Deploy. DAR package is simply a jar file with .dar extension, containing an additional manifest: `deployit-manifest.xml`. You can find more details in [DAR packaging manual](https://docs.xebialabs.com/xl-deploy/latest/packagingmanual.html).

By default Gradle plugin looks for the manifest file in the following location:

    src/main/dar/deployit-manifest.xml

You can override this location in your `build.gradle`:

    xldeploy {
        manifest = file('my/other/location/deployit-manifest.xml')
    }

Here is an example manifest file:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<udm.DeploymentPackage version="${noSnapshot(project.version)}" application="PetClinic">
  <deployables>
    <jee.War name="PetClinic" file="${artifact(project.war)}" />
    <file.File name="config-file" file="${artifact(project.file('my-config.xml'))}">
      <targetPath>/tmp/</targetPath>
    </file.File>
    <file.File name="mysqlDriver" file="${artifact(dependency('mysql:mysql-connector-java:2.0.14'))}">
      <targetPath>/tmp/</targetPath>
    </file.File>
    <cmd.Command name="test-command">
      <commandLine>echo "test"</commandLine>
    </cmd.Command>
  </deployables>
</udm.DeploymentPackage>
```

It defines a [_udm.DeploymentPackage_](https://docs.xebialabs.com/releases/4.5/xl-deploy/udmcireference.html#udmdeploymentpackage) - a version of your application, with all "deployables" defined in it. First of all, it contains artifacts: deployables with content, like .war packages, .jar libraries, .sql scripts etc. Also it can contain content-less deployables like command-line scripts to execute during deployment.

The `deployment-manifest.xml` file in your project is treated as a template, so that you can insert dynamic values using `${...}` expressions. Following root objects are available in the template:

* `project` - the Gradle project to which the `dar` task belongs. Using `project` you get access to all the objects that you use in your `build.gradle`, like `project.version`, `project.myTask`, `project.file('some/path')` etc.
* `artifact` - a function that adds an artifact. Result of evaluation of this function is the path within DAR package where given file will be placed. So it only makes sense to use this function within the `file="artifact(...)"` attribute. The single parameter of this function can have one of the following types:

    * a file, e.g. `artifact(project.file('some/file.txt'))`;
    * a dependency, e.g. `artifact(dependency('mysql:mysql-connector-java:2.0.14'))`, so that required library can be included into DAR package;
    * a Gradle archive task: `jar`, `war`, `ear` etc., e.g. `artifact(project.war)` will include the WAR file generated by your project.

* `dependency` - as described above, a function to be used to include a dependency artifact which makes sure that the dependency is resolved during the `dar` task execution.
* `noSnapshot` - a helper function which replaces `SNAPSHOT` with a timestamp in the version.

## `deploy` task

`deploy` task does two things:

1. uploads the generated .dar file to the XL Deploy server;
2. optionally runs a deployment job in XL Deploy to install your application to an environment.

To run the task you first need to configure general connection details:

    xldeploy {
      xldUrl = "http://localhost:4516/"
      xldUsername = "admin"
      xldPassword = "admin"
    }

The `xldUrl` is http://localhost:4516/ by default. `xldUsername` and `xldPassword` can be also configured in your `~/.gradle/gradle.properties` file instead of `build.gradle`, so that you don't have to store credentials in source files.

The details of deployment task are configured as task properties:

    tasks.deploy.configure {
      environmentId = "Environments/local"
    }

Here is the list of configurable properties of the `deploy` task:

* `environmentId` - the ID of the environment configured in XL Deploy where you want your application to be deployed. If the `environmentId` is not specified then the task does not perform deployment and only uploads the DAR package.
* `skipMode` - activate the skip mode: generate the plan, skip all the steps, validate the task. `false` by default.
* `orchestrator` - sets the orchestrator used during the deployment.
* `testMode` - activates the test mode: generates the plan, displays all the steps and validates the task but does not run it. `false` by default.
* `deletePreviouslyDeployedDar` - deletes the previously deployed dar. It can be useful if you work with SNAPSHOT versions that you don't want to keep in your repository. `false` by default.
* `generateDeployedOnUpgrade` - controls whether during the upgrade operation the deployed objects should be generated like in an initial deployment. For security reasons, the default value is false but should be set to true to apply the modifications during upgrade (e.g. in case of new EAR, removed links etc.) .
* `cancelTaskOnError` - when a task breaks with an error, it is cancelled. Sometimes though it can be useful to check in XL Deploy UI why the task failed. To keep failed tasks you can set this flag to `false`. By default it is set to `true`.

# Feedback

If you find any issues with the `gradle-xld-plugin`, please post them in [GitHub issues page](https://github.com/xebialabs-community/gradle-xld-plugin/issues).
