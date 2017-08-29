#!/usr/bin/groovy

def call(Object ctx, String buildName, int timeOutSecs = 600) {
  ctx.openshift.withCluster() {
    def build = ctx.openshift.selector('build', buildName)
    def buildObject = null
    try {
      buildObject = build.object()
    } catch (e) {
      buildObject = null
    }
    if (buildObject == null) {
      ctx.error("build ${buildName} doesn't exist")
    }
  
    ctx.timeout(time: timeOutSecs, unit: 'SECONDS') {
      build.watch {
        def phase = it.object().status.phase
        return phase != "New" && phase != "Pending" && phase != "Running"
      }
    }
    if (build.object().status.phase != "Complete") {
      build.logs()
      ctx.error("build ${buildName} did not complete successfully (${build.object().status.phase})")
    }
  }
}
