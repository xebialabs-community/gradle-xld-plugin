package com.xebialabs.gradle.plugins.xldeploy

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

}
