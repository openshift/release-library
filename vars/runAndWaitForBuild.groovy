#!/usr/bin/groovy

def call(Object ctx, String buildName, Object createFn, int timeOutSecs = 3600) {
  ctx.openshift.withCluster() {
    def build = ctx.openshift.selector('build', buildName)
    def buildObject = null
    try {
      buildObject = build.object()
    } catch (e) {
      buildObject = null
    }
    if (buildObject != null) {
      deleteBuild(ctx, buildName)
    }
    createFn()
    waitForBuild(ctx, buildName, timeOutSecs)
  }
}
