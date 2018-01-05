package com.redhat.openshift

/**
 * ReadOnlyInfoCache holds information that we could
 * resolve again but use often.
 */
interface ReadOnlyInfoCache {
  String BuildName()

  String JobID()

  String RunID()

  String JenkinsNamespace()
}