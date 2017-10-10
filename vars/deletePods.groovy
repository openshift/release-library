#!/usr/bin/groovy

def call(Object ctx, Object selector) {
  ctx.openshift.withCluster() {
    def pods = ctx.openshift.selector("pods", selector)
    for (i = 0; i < pods.size(); i++) {
        echo "Deleting pod ${pods[i].name()}"
    }
    pods.delete()
  }
}