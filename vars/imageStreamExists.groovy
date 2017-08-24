#!/usr/bin/env groovy

def call(Object ctx, String imageStreamName) {
  def exists = false
  ctx.openshift.withCluster() {
    try {
      def obj = ctx.openshift.selector('is', imageStreamName).object()
      exists = (obj != null)
    } catch(e) {
      exists = false
    }
  }
  return exists
}
