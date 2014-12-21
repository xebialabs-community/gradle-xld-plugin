package com.xebialabs.gradle.plugins.xldeploy

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

class XlDeployPluginTest {

  private Project project

  @Before
  public void before() {
    project = ProjectBuilder.builder().build()
    project.apply plugin: XlDeployPlugin
  }

  @Test
  public void tasksAreAdded() {
    assertTrue(project.tasks.dar instanceof DarTask)
    assertTrue(project.tasks.deploy instanceof DeployTask)
  }

  @Test
  public void addsExtensionWithDefaultManifestLocation() {
    def ext = project.extensions.getByName(XlDeployPlugin.PLUGIN_EXTENSION_NAME) as XlDeployPluginExtension
    assertNotNull(ext)
    assertNotNull(ext.manifest)
    assertTrue(ext.manifest.toString().endsWith('deployit-manifest.xml'))
  }

}
