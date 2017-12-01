package com.redhat.openshift

/**
 * TagSpecification contains configuration for tagging
 * a full set of release images.
 */
class TagSpecification implements Serializable {
  /**
   * namespace and tag are used to locate stable images
   * to tag into a full release.
   */
  String namespace
  String tag

  /**
   * tagOverrides holds a mapping of
   */
  // TODO: finish doc
  Map<String, String> tagOverrides
}