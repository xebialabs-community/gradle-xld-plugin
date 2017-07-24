/*
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.gradle.xldeploy

import groovy.text.SimpleTemplateEngine
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.Dependency
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.DefaultTask
import org.xml.sax.SAXException

import java.text.SimpleDateFormat

class DarConfigurationTask extends DefaultTask {

    public static final String DAR_CONFIGURATION_NAME = "dar"

    private def xldExtension = project.extensions.getByName(XlDeployPlugin.PLUGIN_EXTENSION_NAME) as XlDeployPluginExtension

    private DarTask darTask

    protected ManifestAndArtifacts evaluatedManifest

    DarConfigurationTask() {
        evaluatedManifest = null

        project.afterEvaluate {
            doAfterEvaluate()
        }
    }

    protected void doAfterEvaluate() {
        if (getDarManifest()?.exists()) {
            logger.info("DAR manifest file exists so processing it directly: ${getDarManifest()}")
            processManifest()
        } else {
            logger.info("DAR manifest file does not exist so it will be processed when executing 'dar' task")
        }
    }

    @InputFile
    File getDarManifest() {
        xldExtension.manifest?.exists() ? project.file(xldExtension.manifest) : null
    }

    @TaskAction
    protected void darCopy() {
        if (!evaluatedManifest) {
            processManifest()
        }

        evaluatedManifest.manifest.parentFile.mkdirs()
        def fw = new FileWriter(evaluatedManifest.manifest)
        fw.write(evaluatedManifest.resolvedManifestContent)
        fw.close()

        addToCopySpec(true)

        ensureAllSourcesArePresent()
    }

    protected void processManifest() {
        evaluatedManifest = analyzeManifest()

        darTask.rootSpec.into('') {
            from this.evaluatedManifest.manifest
        }

        addToCopySpec(false)
    }

    protected void addToCopySpec(boolean dependencies) {
        evaluatedManifest.artifactPathToCopyable.each { entryPath, object ->
            if (object instanceof Dependency ^ !dependencies) {
                def f = new File(entryPath)
                def entryFolder = f.getParent() ?: ''
                def entryFileName = f.getName()

                darTask.rootSpec.into(entryFolder) {
                    from (dependencies ? resolveDependency(object as Dependency) : object)
                    rename { ignored ->
                        entryFileName
                    }
                }
            }
        }
    }

    protected ManifestAndArtifacts analyzeManifest() throws ProjectConfigurationException {
        if (!getDarManifest()?.exists()) {
            throw new ProjectConfigurationException("DAR manifest file does not exist: [${getDarManifest()}]",
                    new FileNotFoundException(getDarManifest()?.toString()))
        }
        try {

            def result = renderManifest()

            logger.debug("Resolved manifest content:\n${result.resolvedManifestContent}")
            logger.debug("DAR artifacts mapping: ${result.artifactPathToCopyable}")

            try {
                new XmlParser().parseText(result.resolvedManifestContent)
            } catch (SAXException e) {
                throw new ProjectConfigurationException("Could not parse resolved XML content of DAR manifest file. " +
                        "Run with --debug option to see the problematic XML content", e)
            }

            logger.info("Found ${result.artifactPathToCopyable.size()} artifacts referenced from deployit-manifest.xml")

            result

        } catch (GroovyRuntimeException e) {
            throw new ProjectConfigurationException("Failed to process DAR manifest file [${getDarManifest()}]", e)
        }
    }

    protected ManifestAndArtifacts renderManifest() {
        def template = new SimpleTemplateEngine().createTemplate(getDarManifest())

        def result = new ManifestAndArtifacts()
        def binding = createBinding(result)

        result.manifest = new File(this.temporaryDir, 'deployit-manifest.xml')
        def sw = new StringWriter()
        template.make(binding).writeTo(sw)
        result.resolvedManifestContent = sw.toString()

        result
    }

    protected Map<String, Object> createBinding(ManifestAndArtifacts result) {
        [
                project: project,

                artifact: { Object o ->
                    def pathAndFile = evaluateCopySource(o)
                    result.artifactPathToCopyable[pathAndFile.entryPath] = pathAndFile.copySource
                    pathAndFile.entryPath
                },

                dependency: { Object o ->
                    Dependency d = project.dependencies.add(DAR_CONFIGURATION_NAME, o)
                    d
                },

                noSnapshot: { Object o ->
                    snapshotToTimestamp(o)
                }
        ]
    }

    protected static String snapshotToTimestamp(Object o) {
        String version = String.valueOf(o)
        if (version.contains("SNAPSHOT")) {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss")
            version = version.replace("SNAPSHOT", format.format(new Date()))
        }
        version
    }

    protected Set<File> resolveDependency(Dependency dependency) {
        project.configurations.getByName(DAR_CONFIGURATION_NAME).getResolvedConfiguration()
                .getFiles(new Spec<Dependency>() {
            @Override
            boolean isSatisfiedBy(Dependency element) {
                return element == dependency
            }
        })
    }

    protected EvaluatedCopySource evaluateCopySource(Object o) {
        if (o instanceof String) {
            return evaluateFilePath(new File(o), o)
        }
        if (o instanceof File) {
            return evaluateFilePath(o, o)
        }
        if (o instanceof AbstractArchiveTask) {
            return evaluateFilePath(o.archivePath, o)
        }
        if (o instanceof Dependency) {
            return evaluateDependencyPath(o)
        }
        throw new ProjectConfigurationException("Unexpected type of artifact provided: ${o.getClass()} ($o). " +
                "Supported types are: String, File, AbstractArchiveTask", null)
    }

    protected EvaluatedCopySource evaluateFilePath(File f, Object object) {
        def relativeParent
        if (f.getParentFile() && f.getParentFile().getAbsolutePath().startsWith(project.projectDir.getAbsolutePath())) {
            relativeParent = f.getParentFile().getAbsolutePath()
                    .substring(project.projectDir.getAbsolutePath().length())
                    .replace('\\', '/')
        } else {
            relativeParent = '/' + UUID.randomUUID()
        }
        new EvaluatedCopySource().with {
            entryPath = 'artifacts' + relativeParent + '/' + f.getName()
            copySource = object
            it
        }
    }

    protected EvaluatedCopySource evaluateDependencyPath(Dependency d) {
        new EvaluatedCopySource().with {
            entryPath = "artifacts/${d.getGroup()}/${d.getName()}-${d.getVersion()}.jar"
            copySource = d
            it
        }
    }

    protected void ensureAllSourcesArePresent() {
        evaluatedManifest.artifactPathToCopyable.values().each { copyable ->
            if (copyable instanceof File && !copyable.exists()) {
                throw new FileNotFoundException("DAR artifact source file not found: $copyable. " +
                        "Please make sure it is present before the 'dar' task runs")
            }
            if (copyable instanceof AbstractArchiveTask) {
                copyable.outputs.getFiles().each { f ->
                    if (!f.exists()) {
                        throw new FileNotFoundException("Outputs not found for $copyable which is used as an artifact of the DAR file: $f. " +
                                "Please make sure $copyable is executed before $this")
                    }
                }
            }
        }
    }

    private class EvaluatedCopySource {
        String entryPath
        Object copySource
    }

    private class ManifestAndArtifacts {
        String resolvedManifestContent
        File manifest
        Map<String, Object> artifactPathToCopyable = [:]
    }

    DarTask getDarTask() {
        return darTask
    }

    void setDarTask(DarTask darTask) {
        this.darTask = darTask
    }
}
