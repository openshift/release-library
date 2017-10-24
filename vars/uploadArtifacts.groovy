#!/usr/bin/groovy
def call(Object ctx, String artifactDir) {
  ctx.sh "oc get configmap/logging-config -o jsonpath='{ .data.logging-config\\.json }' > logging-config.json"
  ctx.sh "oc get secret/gce -o jsonpath='{ .data.gce\\.json }' | base64 -d > gce.json"
  def loggingConfig = ctx.readJSON file: 'logging-config.json'
  def curDir = ctx.pwd()
  loggingConfig['gce-credentials-file'] = "${curDir}/gce.json"
  loggingConfig['artifact-dir'] = artifactDir
  loggingConfig['configuration-file'] = "${curDir}/job-config.json"
  writeJSON file: 'logging-config.json', json: loggingConfig
  ctx.sh "release-ci save-config --config-path \"${curDir}/logging-config.json\""
  ctx.sh "release-ci upload --config-path \"${curDir}/logging-config.json\""
}
