package com.xebialabs.gradle.plugins.xldeploy

import groovy.text.SimpleTemplateEngine
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.xml.sax.SAXException

class DarTask extends Jar {

  public static final String DAR_EXTENSION = 'dar'

  private def ext = project.extensions.getByName(XlDeployPlugin.PLUGIN_EXTENSION_NAME) as XlDeployPluginExtension

  DarTask() {
    extension = DAR_EXTENSION

    project.afterEvaluate {

      def manifestAndArtifacts = analyzeManifest()

      rootSpec.into('') {
        from manifestAndArtifacts.manifest
      }
      manifestAndArtifacts.artifactPathToFile.each { entryFolder, object ->
        rootSpec.into(entryFolder) {
          from object
        }
      }
    }
  }

  @Override
  protected void copy() {
    def manifest = analyzeManifest()
    def fw = new FileWriter(manifest.manifest)
    fw.write(manifest.resolvedManifestContent)
    fw.close()
    super.copy()
  }

  @InputFile
  File getDarManifest() {
    return project.file(ext.manifest)
  }

  protected ManifestAndArtifacts analyzeManifest() throws ProjectConfigurationException {
    if (!getDarManifest().exists()) {
      throw new ProjectConfigurationException("DAR manifest file does not exist: [${getDarManifest()}]",
          new FileNotFoundException(getDarManifest().toString()))
    }
    try {
      def template = new SimpleTemplateEngine().createTemplate(getDarManifest())

      def result = new ManifestAndArtifacts()
      def artifactToDarEntry = { Object o ->
        def pathAndFile = resolveFileAndDarPath(o)
        result.artifactPathToFile[pathAndFile.entryFolder] = pathAndFile.file
        return pathAndFile.entryPath
      }

      def binding = [
          project: project,
          artifact: artifactToDarEntry
      ]

      result.manifest = new File(this.temporaryDir, 'deployit-manifest.xml')
      def sw = new StringWriter()
      template.make(binding).writeTo(sw)
      result.resolvedManifestContent = sw.toString()

      logger.debug("Resolved manifest content:\n${result.resolvedManifestContent}")
      logger.debug("DAR artifacts mapping:\n${result.artifactPathToFile}")

      try {
        new XmlParser().parseText(result.resolvedManifestContent)
      } catch (SAXException e) {
        throw new ProjectConfigurationException("Could not parse resolved XML content of DAR manifest file. " +
            "Run with --debug option to see the problematic XML content", e)
      }

      logger.info("Found ${result.artifactPathToFile.size()} artifacts referenced from deployit-manifest.xml")

      result

    } catch (GroovyRuntimeException e) {
      throw new ProjectConfigurationException("Failed to process DAR manifest file [${getDarManifest()}]", e)
    }
  }

  protected PathAndFile resolveFileAndDarPath(Object o) {
    File f
    if (o instanceof String) {
      f = new File(o)
    } else if (o instanceof File) {
      f = o
    } else if (o instanceof AbstractArchiveTask) {
      f = o.archivePath
    } else {
      throw new ProjectConfigurationException("Unexpected type of artifact provided: ${o.getClass()} ($o). " +
          "Supported types are: String, File, AbstractArchiveTask", null)
    }

    String relativeParent
    if (f.getParentFile().getAbsolutePath().startsWith(project.projectDir.getAbsolutePath())) {
      relativeParent = f.getParentFile().getAbsolutePath().substring(project.projectDir.getAbsolutePath().length())
    } else {
      relativeParent = UUID.randomUUID()
    }

    return new PathAndFile().with {
      entryFolder = 'artifacts' + relativeParent
      entryPath = entryFolder + '/' + f.getName()
      file = o
      it
    }

  }

  private class ManifestAndArtifacts {
    String resolvedManifestContent
    File manifest
    Map<String, File> artifactPathToFile = [:]
  }

  private class PathAndFile {
    String entryFolder
    String entryPath
    Object file
  }

}
