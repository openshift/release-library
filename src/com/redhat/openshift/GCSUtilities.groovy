package com.redhat.openshift

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

import static com.redhat.openshift.TestUtilities.RELEASE_CI_PATH

class GCSUtilities implements Serializable {
  static final String GCS_DIR = "gcs"
  static final String ARTIFACT_DIR = "artifacts"
  static final String BUILD_LOG = "build-log.txt"
  static final String STARTED_METADATA = "started.json"
  static final String FINISHED_METADATA = "finished.json"

  static String GCSDir(Object ctx) {
    String currentDir = ctx.pwd()
    return String.format("%s/%s", currentDir, GCS_DIR)
  }

  static String ArtifactDir(Object ctx) {
    return String.format("%s/%s", GCSDir(ctx), ARTIFACT_DIR)
  }

  static void GenerateStartedMetadata(Object ctx) {
    ctx.echo "GenerateStartedMetadata: generating started metadata"
    long unixTime = System.currentTimeMillis() / 1000L
    String startedMetadata = JsonOutput.prettyPrint(JsonOutput.toJson([timestamp: unixTime]))
    ctx.dir(GCSDir(ctx)) {
      ctx.echo "GenerateStartedMetadata: writing to ${STARTED_METADATA}: ${startedMetadata}"
      ctx.writeFile file: STARTED_METADATA, text: startedMetadata
    }
    ctx.echo "GenerateStartedMetadata: generated started metadata"
  }

  static void GenerateFinishedMetadata(Object ctx) {
    ctx.echo "GenerateFinishedMetadata: generating finished metadata"
    long unixTime = System.currentTimeMillis() / 1000L
    String result = ctx.currentBuild.currentResult
    boolean passed = (result == "SUCCESS")
    String finishedMetadata = JsonOutput.prettyPrint(JsonOutput.toJson([
      timestamp: unixTime,
      passed   : passed,
      result   : result
    ]))
    ctx.dir(GCSDir(ctx)) {
      ctx.echo "GenerateFinishedMetadata: writing to ${FINISHED_METADATA}: ${finishedMetadata}"
      ctx.writeFile file: FINISHED_METADATA, text: finishedMetadata
    }
    ctx.echo "GenerateFinishedMetadata: generated finished metadata"
  }

  static void UploadArtifacts(Object ctx, Object env) {
    ctx.echo "UploadArtifacts: uploading artifacts"
    String curDir = ctx.pwd()
    if (!ctx.fileExists("${curDir}/logging-config.json")) {
      generateModifiedLoggingConfig(ctx, env)
    }
    ctx.sh "${RELEASE_CI_PATH} upload --config-path \"${curDir}/logging-config.json\""
    ctx.echo "UploadArtifacts: uploaded artifacts"
  }

  private static void generateModifiedLoggingConfig(Object ctx, Object env) {
    ctx.echo "generateModifiedLoggingConfig: generating modified logging configuration for upload"
    String curDir = ctx.pwd()
    String artifactDir = GCSDir(ctx)
    ctx.openshift.withCluster() {
      String loggingConfigurationJSON = ctx.openshift.selector("configmap", "logging-config").object().data["logging-config.json"]
      def loggingConfig = new JsonSlurperClassic().parseText(loggingConfigurationJSON)
      loggingConfig["artifact-dir"] = "${artifactDir}"
      loggingConfig["configuration-file"] = "${curDir}/job-config.json"
      loggingConfig["gce-credentials-file"] = "${curDir}/gce.json"
      ctx.writeFile file: "logging-config.json", text: JsonOutput.prettyPrint(JsonOutput.toJson(loggingConfig))

      String base64EncodedSecret = ctx.openshift.selector("secret", "gce").object().data["gce.json"]
      // TODO: decode this using Java?
      String secret = ctx.sh(returnStdout: true, script: "base64 -d <<<'${base64EncodedSecret}'").trim()
      ctx.writeFile file: "gce.json", text: secret

      ctx.withEnv([
        "JOB_NAME=${env.JOB_NAME}",
        "BUILD_NUMBER=${env.BUILD_NUMBER}",
        "REPO_OWNER=${env.REPO_OWNER}",
        "REPO_NAME=${env.REPO_NAME}",
        "PULL_BASE_REF=${env.PULL_BASE_REF}",
        "PULL_BASE_SHA=${env.PULL_BASE_SHA}",
        "PULL_REFS=${env.PULL_REFS}",
        "PULL_NUMBER=${env.PULL_NUMBER}",
        "PULL_PULL_SHA=${env.PULL_PULL_SHA}"
      ]) {
        ctx.sh "${RELEASE_CI_PATH} save-config --config-path \"${curDir}/logging-config.json\""
      }
    }
    ctx.echo "generateModifiedLoggingConfig: generated modified logging configuration for upload"
  }
}