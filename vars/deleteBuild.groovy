#!/usr/bin/env groovy

def call(Object ctx, String buildName) {
  ctx.openshift.withCluster() {
    ctx.echo "Deleting build ${buildName}"
    ctx.openshift.delete("build/${buildName}", "--ignore-not-found")
  }
}
