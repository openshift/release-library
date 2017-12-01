package com.redhat.openshift

/**
 * InfoCache holds information that we could resolve
 * again but use often. This is an unsafe read/write
 * reference and should not be passed out of the main
 * pipeline. It should be passed as a read-only copy
 * instead.
 */
class InfoCache implements Serializable, ReadOnlyInfoCache {
  /**
   * BuildName is an identifier for the build created
   * by hashing the input configuration for the build.
   * It will therefore match if the same inputs are
   * used again. Using this as an identifier is how we
   * reuse artifacts between builds.
   */
  String BuildName

  /**
   * JobID is a unique identifier for this build job,
   * usually consisting of the job name and build
   * number, formatted to be usable in a Kubernetes
   * label field.
   */
  String JobID

  /**
   * RunID is a unique identifier for this build job
   * and is used to label objects that need to be
   * cleaned up at the end of the pipeline run.
   */
  // TODO How is this different from above?
  String RunID

  /**
   * JenkinsNamespace is the namespace that the job
   * is deployed in.
   */
  String JenkinsNamespace

  @Override
  String BuildName() {
    return this.BuildName
  }

  @Override
  String JobID() {
    return this.JobID
  }

  @Override
  String RunID() {
    return this.RunID
  }

  @Override
  String JenkinsNamespace() {
    return this.JenkinsNamespace
  }
}