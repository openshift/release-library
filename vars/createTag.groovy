#!/usr/bin/groovy

def call(Object ctx, String src, dest) {
  ctx.openshift.withCluster() {
  	echo "Tagging ${src} as ${dest}"
    ctx.openshift.tag(src, dest)
  }
}
