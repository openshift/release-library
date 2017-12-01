package com.redhat.openshift

/**
 * ImageReference refers to an image that exists outside
 * of the pipeline and is identified with a full image pull
 * specification.
 */
class ImageReference implements Serializable {
  public String namespace
  public String name
  public String tag

  String PullSpec() {
    return "${this.namespace}/${this.name}:${this.tag}"
  }
}