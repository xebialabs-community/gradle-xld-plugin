package com.xebialabs.gradle.plugins.xldeploy

import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.tasks.bundling.War
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import org.xml.sax.SAXException

import java.util.zip.ZipFile

import static org.junit.Assert.fail

class DarTaskTest {

  private Project project
  private DarTask dar

  @Before
  public void before() {
    project = ProjectBuilder.builder().withProjectDir(new File('src/test/resources/PetClinic')).build()
    project.apply plugin: 'war'
    project.apply plugin: 'com.xebialabs.xl-deploy'
    project.version = "1.0-SNAPSHOT"
    dar = project.tasks.dar as DarTask
  }

  @Test
  public void projectVersionIsResolved() {
    def manifest = dar.analyzeManifest().resolvedManifestContent
    assert manifest.contains('version="1.0-SNAPSHOT"')
  }

  @Test
  public void archiveTaskOutputIsResolved() {
    def result = dar.analyzeManifest()
    def expectedPath = 'artifacts/build/libs/test-1.0-SNAPSHOT.war'
    assert result.resolvedManifestContent.contains(
      "<jee.War name=\"PetClinic\" file=\"$expectedPath\" />")
    assert result.artifactPathToFile[expectedPath] instanceof War
  }

  @Test
  public void simpleFileIsResolved() {
    def result = dar.analyzeManifest()
    assert result.resolvedManifestContent.contains(
      '<file.File name="file-1" file="artifacts/file.txt" />')
    assert (result.artifactPathToFile['artifacts/file.txt'] as File).exists()
  }

  @Test
  public void fileReferencesOutsideProjectAreResolvedAndPutInUUIDFolders() {
    def manifest = dar.analyzeManifest().resolvedManifestContent
    assert manifest =~ /name="file-2" file="artifacts\/.{36}\/file-2.txt"/
  }

  @Test
  public void failsOnInvalidXML() {
    def ext = project.extensions.getByName(XlDeployPlugin.PLUGIN_EXTENSION_NAME) as XlDeployPluginExtension
    ext.manifest = project.file('src/main/dar/deployit-manifest-invalid.xml')
    try {
      dar.analyzeManifest()
      fail "Expected error as DAR manifest is not a valid XML file"
    } catch (ProjectConfigurationException e) {
      assert e.cause instanceof SAXException
    }
  }

  @Test
  public void putsArtifactsInDar() {
    dar.destinationDir.mkdirs()
    dar.doAfterEvaluate()
    dar.copy()
    File zipFile = new File(dar.destinationDir, 'test-1.0-SNAPSHOT.dar')
    assert zipFile.exists()
    ZipFile zip = new ZipFile(zipFile)
    assert zip.getEntry('artifacts/file.txt') != null
    def uuidEntry
    zip.entries().each { e ->
      if (e.name.endsWith('file-2.txt')) {
        uuidEntry = e
      }
    }
    assert uuidEntry != null
  }

}
