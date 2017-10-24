#!/usr/bin/groovy
def call(Object ctx, String artifactDir) {
  ctx.sh "oc get configmap/logging-config -o jsonpath='{ .data.logging-config\\.json }' > logging-config.json"
  ctx.sh "oc get secret/gce -o jsonpath='{ .data.gce\\.json }' | base64 -d > gce.json"
  def loggingConfig = ctx.readJSON file: 'logging-config.json'
  def curDir = ctx.pwd()
  loggingConfig['gce-credentials-file'] = "${curDir}/gce.json"
  loggingConfig['artifact-dir'] = artifactDir
  loggingConfig['configuration-file'] = "${curDir}/job-config.json"
  ctx.writeJSON file: 'logging-config.json', json: loggingConfig
  ctx.withEnv([
    "JOB_NAME=${env.JOB_NAME}",
    "BUILD_NUMBER=${env.BUILD_NUMBER}",
    "REPO_OWNER=${params.REPO_OWNER}",
    "REPO_NAME=${params.REPO_NAME}",
    "PULL_BASE_REF=${params.PULL_BASE_REF}",
    "PULL_BASE_SHA=${params.PULL_BASE_SHA}",
    "PULL_REFS=${params.PULL_REFS}",
    "PULL_NUMBER=${params.PULL_NUMBER}",
    "PULL_PULL_SHA=${params.PULL_PULL_SHA}"
  ]) {
    ctx.sh "release-ci save-config --config-path \"${curDir}/logging-config.json\""
  }
  ctx.sh "release-ci upload --config-path \"${curDir}/logging-config.json\""
}
