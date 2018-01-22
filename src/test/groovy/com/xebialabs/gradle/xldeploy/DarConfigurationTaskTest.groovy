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

import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.bundling.War
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import org.xml.sax.SAXException

import java.text.SimpleDateFormat
import java.util.zip.ZipFile

import static org.junit.Assert.fail

class DarConfigurationTaskTest {

  private Project project
  private DarConfigurationTask configureDar
  private DarTask dar

  @Before
  public void before() {
    project = ProjectBuilder.builder().withProjectDir(new File('src/test/resources/HelloDeployment')).build()
    project.apply plugin: 'war'
    project.apply plugin: XlDeployPlugin
    project.version = "1.0"
    project.dependencies {
      project.getRepositories().mavenCentral()
    }

    configureDar = project.tasks.configureDar as DarConfigurationTask
    dar = project.tasks.dar as DarTask
    project.tasks.clean.execute()
  }

  @Test
  public void projectVersionIsResolved() {
    def manifest = configureDar.analyzeManifest().resolvedManifestContent
    assert manifest.contains('version="1.0"')
  }

  @Test
  public void snapshotIsConvertedToTimestamp() {
    project.version = "1.0-SNAPSHOT"
    def date = new SimpleDateFormat("yyyyMMdd").format(new Date())
    def manifest = configureDar.analyzeManifest().resolvedManifestContent
    assert manifest =~ /version="1\.0-$date-\d\d\d\d\d\d"/
  }

  @Test
  public void archiveTaskOutputIsResolved() {
    def result = configureDar.analyzeManifest()
    def expectedPath = 'artifacts/build/libs/test-1.0.war'
    assert result.resolvedManifestContent.contains(
      "<jee.War name=\"HelloDeployment\" file=\"$expectedPath\" />")
    assert result.artifactPathToCopyable[expectedPath] instanceof War
  }

  @Test
  public void simpleFileIsResolved() {
    def result = configureDar.analyzeManifest()
    assert result.resolvedManifestContent.contains(
      '<file.File name="file-1" file="artifacts/file.txt"')
    assert (result.artifactPathToCopyable['artifacts/file.txt'] as File).exists()
  }

  @Test
  public void dependencyIsResolved() {
    def result = configureDar.analyzeManifest()
    def expectedPath = "artifacts/mysql/mysql-connector-java-2.0.14.jar"
    assert result.resolvedManifestContent.contains(
        "<file.Archive name=\"mysqlDriver\" file=\"$expectedPath\"")
    assert result.artifactPathToCopyable.get(expectedPath) instanceof Dependency
  }

  @Test
  public void fileReferencesOutsideProjectAreResolvedAndPutInUUIDFolders() {
    def manifest = configureDar.analyzeManifest().resolvedManifestContent
    assert manifest =~ /name="file-2" file="artifacts\/.{36}\/file-2.txt"/
  }

  @Test
  public void failsOnInvalidXML() {
    def ext = project.extensions.getByName(XlDeployPlugin.PLUGIN_EXTENSION_NAME) as XlDeployPluginExtension
    ext.manifest = project.file('src/main/dar/deployit-manifest-invalid.xml')
    try {
      configureDar.analyzeManifest()
      fail "Expected error as DAR manifest is not a valid XML file"
    } catch (ProjectConfigurationException e) {
      assert e.cause instanceof SAXException
    }
  }

  @Test
  public void putsArtifactsInDar() {
    project.tasks.war.execute()

    dar.destinationDir.mkdirs()
    configureDar.processManifest()
    configureDar.execute()
    dar.execute()
    File zipFile = new File(dar.destinationDir, 'test-1.0.dar')
    assert zipFile.exists()
    ZipFile zip = new ZipFile(zipFile)
    assert zip.getEntry('artifacts/file.txt') != null
    assert zip.getEntry('artifacts/mysql/mysql-connector-java-2.0.14.jar') != null
    assert zip.getEntry('artifacts/build/libs/test-1.0.war') != null

    def uuidEntry
    zip.entries().each { e ->
      if (e.name.endsWith('file-2.txt')) {
        uuidEntry = e
      }
    }
    assert uuidEntry != null
  }

  @Test(expected = FileNotFoundException.class)
  public void failsIfArtifactIsNotPresent() {
    // The WAR task did not run

    dar.destinationDir.mkdirs()
    configureDar.processManifest()
    configureDar.darCopy()
    dar.copy()
  }

  @Test
  public void shouldLetGenerateManifest() {
    def generatedManifest = project.file(project.buildDir.path + "/generated.xml")
    project.extensions.findByType(XlDeployPluginExtension).manifest = generatedManifest

    configureDar.doAfterEvaluate()
    assert configureDar.evaluatedManifest == null

    project.buildDir.mkdir()
    generatedManifest.write("""<udm.DeploymentPackage version="\${project.version}" application="HelloDeployment"/>""")
    configureDar.execute()
    assert configureDar.evaluatedManifest.resolvedManifestContent.contains("version=\"1.0\"")
  }
}
