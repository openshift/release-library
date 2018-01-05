package com.redhat.openshift

import javax.xml.bind.DatatypeConverter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import static com.redhat.openshift.OpenShiftUtilities.Exists

class TestUtilities implements Serializable {
  static final String RELEASE_CI_PATH = "/var/lib/jenkins/tools/release-ci"
  static final String GCE_DATA_PATH = "/usr/share/ansible/openshift-ansible-gce/playbooks/files"

  static void EnsureLoggingComponents(Object ctx) {
    ctx.echo "EnsureLoggingComponents: ensuring that logging components exist"
    ctx.openshift.withCluster() {
      def configMap = [
        "kind"    : "ConfigMap",
        "metadata": [
          "name": "logging-config"
        ],
        "data"    : [
          "logging-config.json": """{
  "process-log": "/var/run/logging/process-log",
  "marker-file": "/var/run/logging/marker",
  "gcs-bucket": "origin-ci-test",
  "gce-credentials-file": "/var/run/secrets/gce/gce.json",
  "artifact-dir": "/var/run/logging",
  "configuration-file": "/var/run/logging/config.json"
}"""
        ]
      ]
      ctx.openshift.apply(configMap)
      if (!Exists(ctx, "Secret", "gce")) {
        ctx.openshift.raw("create", "secret", "generic", "gce")
      }
    }
    ctx.echo "EnsureLoggingComponents: ensured that logging components exist"
  }

  static ReadOnlyInfoCache NewInfoCache(Object ctx, Object env) {
    return new InfoCache(
      BuildName: BuildName(ctx, env),
      JobID: "${env.JOB_NAME.replace("/", "-")}-${env.BUILD_NUMBER}",
      JenkinsNamespace: env.PROJECT_NAME
    )
  }

  static String BuildName(Object ctx, Object env) {
    ctx.echo "BuildName: determining the build name"
    String hash
    ctx.withEnv([
      "REPO_NAME=${env.REPO_NAME}",
      "REPO_OWNER=${env.REPO_OWNER}"
    ]) {
      hash = ctx.sh(script: "${RELEASE_CI_PATH} refhash ${env.PULL_REFS}", returnStdout: true).trim()
    }
    ctx.echo "BuildName: determined the build name to be ${hash}"
    return hash
  }

  /**
   * UniqueNodeName returns a name that will be a valid label field
   * to use in identifying a Pod template used for dynamic Jenkins
   * worker pods. We need the "node" to have a unique name as the
   * configuration for this node is exposed to the global Jenkins
   * configuration while running.
   *
   * Labels can't be more than 63 characters and need to conform to
   * the following regex [a-z0-9]([-a-z0-9]*[a-z0-9])?'), so we hash
   * the unique name, display it in hex and make it all lowercase.
   * This does increase the chances of hash collisions but our names
   * occupy such a small fraction of the input space that we do not
   * need to worry about that.
   */
  static String UniqueNodeName(ReadOnlyInfoCache info, String prefix) {
    byte[] uniqueName = String.format("%s-%s", prefix, info.JobID()).getBytes(StandardCharsets.UTF_8)
    String formattedName = DatatypeConverter.printHexBinary(MessageDigest.getInstance("SHA-256").digest(uniqueName))
    return formattedName.substring(0, 32).toLowerCase()
  }
}