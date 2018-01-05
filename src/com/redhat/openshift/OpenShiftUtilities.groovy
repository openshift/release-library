package com.redhat.openshift

class OpenShiftUtilities implements Serializable {

  /**
   * The following label names are convention for
   * objects created by these pipelines.
   */

  /**
   * The `created-by-ci` label is used for periodic
   * cleanup of objects in the namespace.
   */
  static final String CREATED_BY_CI_LABEL = "created-by-ci"
  static final String CREATED_BY_CI_VALUE = "true"

  /**
   * The `job-id` label is used for cleanup at the
   * end of a pipeline job build.
   */
  static final String JOB_ID_LABEL = "job-id"

  /**
   * The `build` label is used to identify objects
   * from a pipline job build.
   */
  static final String BUILD_LABEL = "build"

  /**
   * The `persists-between-builds` label identifies
   * whether an object should be deleted at the end of
   * a build or not.
   */
  static final String PERSISTS_LABEL = "persists-between-builds"
  static final String PERSISTS_VALUE = "true"
  static final String DOESNT_PERSIST_VALUE = "false"

  static boolean Exists(Object ctx, String type, String name) {
    ctx.echo "Exists: determining if ${type} ${name} exists"
    boolean exists = false
    ctx.openshift.withCluster() {
      exists = ctx.openshift.selector(type, name).exists()
    }
    ctx.echo "Exists: determined that ${type} ${name} exists: ${exists}"
    return exists
  }

  static void WaitForDeployment(Object ctx, String deploymentConfigName) {
    ctx.echo "WaitForDeployment: waiting for DeploymentConfig ${deploymentConfigName} to complete"
    ctx.openshift.withCluster() {
      ctx.timeout(time: 600, unit: 'SECONDS') {
        ctx.openshift.selector("deploymentconfig", deploymentConfigName).rollout().status("-w")
      }
    }
    ctx.echo "WaitForDeployment: waited DeploymentConfig ${deploymentConfigName} to complete"
  }

  /**
   * WaitForImageStreamTag waits for an ImageStreamTag to exist
   * but _must_ be called within a cluster and project context.
   */
  static void WaitForImageStreamTag(Object ctx, Object imageStream, String requestedTag) {
    ctx.timeout(time: 1, unit: 'HOURS') {
      while (!imageStream.exists()) {
        ctx.echo "WaitForImageStreamTag: waiting for ImageStream to exist"
        ctx.sleep 30
      }
      String name = imageStream.object().metadata.name
      ctx.echo "WaitForImageStreamTag: waiting for ImageStreamTag ${name}:${requestedTag} to exist"
      imageStream.watch {
        for (tag in it.object().status.tags) {
          if (tag.tag == requestedTag && tag.items != null && tag.items.size() > 0) {
            ctx.echo "WaitForImageStreamTag: waited for ImageStreamTag ${name}:${requestedTag} to exist"
            return true
          }
        }
        return false
      }
    }
  }

  /**
   * WaitFor waits for an object to exist in the Jenkins namespace.
   */
  static void WaitFor(Object ctx, String type, String name) {
    ctx.echo "WaitFor: waiting for ${type} ${name} to exist"
    ctx.timeout(time: 1, unit: 'HOURS') {
      ctx.openshift.withCluster() {
        def object = ctx.openshift.selector(type, name)
        while (!object.exists()) {
          ctx.sleep 30
        }
      }
    }
  }
}