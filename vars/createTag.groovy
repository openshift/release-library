#!/usr/bin/groovy

def call(Object ctx, String src, dest) {
  ctx.openshift.withCluster() {
    ctx.openshift.tag(src, dest)
  }
}
