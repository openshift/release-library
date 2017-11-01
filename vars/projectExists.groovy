#!/usr/bin/groovy

def call(Object ctx, String projectName, String token) {
  def exists = false
  ctx.openshift.withCluster() {
    try {
      if (token) {
        ctx.openshift.doAs(token) {
          exists = ctx.openshift.selector('project', projectName).exists()
        }
      } else {
        exists = ctx.openshift.selector('project', projectName).exists()
      }
    } catch (e) {
      exists = false
    }
  }
  ctx.echo "Project ${projectName} exists: ${exists}"
  return exists
}
