#!/usr/bin/env groovy

def call(Object ctx, String serviceName) {
  def exists = false
  ctx.openshift.withCluster() {
    try {
      exists = ctx.openshift.selector('svc', serviceName).exists()
    } catch(e) {
      exists = false
    }
  }
  echo "Service ${serviceName} exists: ${exists}"
  return exists
}
