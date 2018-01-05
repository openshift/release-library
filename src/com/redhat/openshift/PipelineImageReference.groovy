package com.redhat.openshift

/**
 * PipelineImageReference refers to an image that is built
 * to cache artifacts as part of the pipeline build and
 * identified with only it's tag.
 */
class PipelineImageReference implements Serializable {
  public String tag

  String PullSpec() {
    return this.tag
  }
}