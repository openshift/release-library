package com.redhat.openshift

/**
 * Step produces an image on the build ImageStream.
 * Steps should POST to the OpenShift API and exit,
 * they should not wait for e.g. a Build to complete.
 */
interface Step {
  /**
   * Run executes the step, making use of the pipeline
   * as well as environment and the info cache. The
   * list of builds launched is returned by name.
   */
  List<String> Run(Object ctx, Object env, ReadOnlyInfoCache info)

  /**
   * From returns the image pull specs that this step
   * builds on. This pull specs may be incomplete and
   * should only be used to compare to other Step's
   * To() pull specs.
   */
  List<String> From()

  /**
   * To returns the image pull spec that this step
   * produces. This pull spec may be incomplete and
   * should only be used to compare to other Step's
   * From() pull specs.
   */
  String To()

  /**
   * ID identifies the step with it's configuration.
   */
  String ID()
}