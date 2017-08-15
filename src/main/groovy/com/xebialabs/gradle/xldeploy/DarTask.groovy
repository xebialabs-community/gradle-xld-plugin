/*
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.gradle.xldeploy

import org.gradle.api.tasks.bundling.Jar

/**
 * This task creates a DAR package with provided manifest and artifacts in it.
 */
class DarTask extends Jar {
    public static final String DAR_EXTENSION = 'dar'

    DarTask() {
        extension = DAR_EXTENSION
    }
}
