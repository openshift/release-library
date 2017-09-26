#!/usr/bin/groovy

def call(Object ctx) {
  ctx.openshift.withCluster() {
    def o = ctx.openshift
    if (!o.selector("configmap/logging-config").exists()) {
      ctx.applyTemplate(ctx, "logging-configmap.yaml")
    }
    if (!o.selector("secrets/gce").exists()) {
      o.raw("create secret generic gce")
    }
  }
}