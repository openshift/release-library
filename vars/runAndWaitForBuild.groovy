#!/usr/bin/groovy

def isFailed(Object build) {
  def phase = build.status.phase
  return (phase == "Failed" || phase == "Cancelled" || phase == "Error")
}

def call(Object ctx, String buildName, Object createFn) {
  ctx.openshift.withCluster() {
    def build = ctx.openshift.selector('build', buildName)
    def buildObject = null
    def createBuild = false
    try {
      buildObject = build.object()
    } catch (e) {
      buildObject = null
    }
    if (buildObject == null) {
      createBuild = true
    } else if (isFailed(buildObject)) {
      deleteBuild(ctx, buildName)
      createBuild = true
    }

    if (createBuild) {
      createFn()
    }
    waitForBuild(ctx, buildName)
  }
}
