/*
 * Copyright 2019 XEBIALABS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.xebialabs.gradle.xldeploy

import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

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

  /**
   * LogLevel to use for deployment messages, defaults to INFO
   */
  String xldDeployLogLevel = LogLevel.INFO

  /**
   * Socket timeout for requests to XLD
   */
  Integer socketTimeout = 10000
}
