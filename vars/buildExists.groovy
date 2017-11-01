#!/usr/bin/groovy

def call(Object ctx, String buildName) {
  def exists = false
  ctx.openshift.withCluster() {
    exists = ctx.openshift.selector('build', buildName).exists()
  }
  ctx.echo "Build ${buildName} exists: ${exists}"
  return exists
}
