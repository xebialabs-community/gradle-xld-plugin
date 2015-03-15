/*
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.gradle.xldeploy

import org.gradle.api.Project

class XlDeployPluginExtension {
  Project project

  /**
   * DAR manifest template location,
   * <pre>src/main/dar/deployit-manifest.xml</pre> by default.
   */
  File manifest

  /**
   * XL Deploy URL, <pre>http://localhost:4516/</pre> by default.
   */
  String xldUrl

  /**
   * XL Deploy xldUsername.
   */
  String xldUsername

  /**
   * XL Deploy xldPassword.
   */
  String xldPassword

  /**
   * Environment ID to deploy to.
   */
  String xldEnvironmentId

}
