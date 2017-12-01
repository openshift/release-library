package com.redhat.openshift

class ImageBuildSpecification implements Serializable {
  /**
   * baseImage is the name of the image this build is FROM
   */
  String from
  /**
   * to is the name of the image this build produces
   */
  String to
  /**
   * contextDir is the path to the build's Dockerfile
   * directory under the repository root
   */
  String contextDir
}