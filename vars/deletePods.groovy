#!/usr/bin/groovy

def call(Object ctx, Object selector) {
  ctx.openshift.withCluster() {
    def pods = ctx.openshift.selector("pods", selector)
    pods.delete()
  }
}