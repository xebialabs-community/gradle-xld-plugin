/*
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
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
  private DarConfigurationTask darConfiguration
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

    darConfiguration = project.tasks.darConfiguration as DarConfigurationTask
    dar = project.tasks.dar as DarTask
    project.tasks.clean.execute()
  }

  @Test
  public void projectVersionIsResolved() {
    def manifest = darConfiguration.analyzeManifest().resolvedManifestContent
    assert manifest.contains('version="1.0"')
  }

  @Test
  public void snapshotIsConvertedToTimestamp() {
    project.version = "1.0-SNAPSHOT"
    def date = new SimpleDateFormat("yyyyMMdd").format(new Date())
    def manifest = darConfiguration.analyzeManifest().resolvedManifestContent
    assert manifest =~ /version="1\.0-$date-\d\d\d\d\d\d"/
  }

  @Test
  public void archiveTaskOutputIsResolved() {
    def result = darConfiguration.analyzeManifest()
    def expectedPath = 'artifacts/build/libs/test-1.0.war'
    assert result.resolvedManifestContent.contains(
      "<jee.War name=\"HelloDeployment\" file=\"$expectedPath\" />")
    assert result.artifactPathToCopyable[expectedPath] instanceof War
  }

  @Test
  public void simpleFileIsResolved() {
    def result = darConfiguration.analyzeManifest()
    assert result.resolvedManifestContent.contains(
      '<file.File name="file-1" file="artifacts/file.txt"')
    assert (result.artifactPathToCopyable['artifacts/file.txt'] as File).exists()
  }

  @Test
  public void dependencyIsResolved() {
    def result = darConfiguration.analyzeManifest()
    def expectedPath = "artifacts/mysql/mysql-connector-java-2.0.14.jar"
    assert result.resolvedManifestContent.contains(
        "<file.Archive name=\"mysqlDriver\" file=\"$expectedPath\"")
    assert result.artifactPathToCopyable.get(expectedPath) instanceof Dependency
  }

  @Test
  public void fileReferencesOutsideProjectAreResolvedAndPutInUUIDFolders() {
    def manifest = darConfiguration.analyzeManifest().resolvedManifestContent
    assert manifest =~ /name="file-2" file="artifacts\/.{36}\/file-2.txt"/
  }

  @Test
  public void failsOnInvalidXML() {
    def ext = project.extensions.getByName(XlDeployPlugin.PLUGIN_EXTENSION_NAME) as XlDeployPluginExtension
    ext.manifest = project.file('src/main/dar/deployit-manifest-invalid.xml')
    try {
      darConfiguration.analyzeManifest()
      fail "Expected error as DAR manifest is not a valid XML file"
    } catch (ProjectConfigurationException e) {
      assert e.cause instanceof SAXException
    }
  }

  @Test
  public void putsArtifactsInDar() {
    project.tasks.war.execute()

    dar.destinationDir.mkdirs()
    darConfiguration.processManifest()
    darConfiguration.execute()
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
    darConfiguration.processManifest()
    darConfiguration.darCopy()
    dar.copy()
  }

  @Test
  public void shouldLetGenerateManifest() {
    def generatedManifest = project.file(project.buildDir.path + "/generated.xml")
    project.extensions.findByType(XlDeployPluginExtension).manifest = generatedManifest

    darConfiguration.doAfterEvaluate()
    assert darConfiguration.evaluatedManifest == null

    project.buildDir.mkdir()
    generatedManifest.write("""<udm.DeploymentPackage version="\${project.version}" application="HelloDeployment"/>""")
    darConfiguration.execute()
    assert darConfiguration.evaluatedManifest.resolvedManifestContent.contains("version=\"1.0\"")
  }
}
