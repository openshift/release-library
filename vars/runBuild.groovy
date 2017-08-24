#!/usr/bin/groovy

def call(Object ctx, String buildConfigName) {
  def build
  ctx.openshift.withCluster() {
    def buildConfig 
    try {
        buildConfig = ctx.openshift.selector("buildconfig", buildConfigName).object()
    } catch(e) {
        buildConfig = null
    }
    if (buildConfig == null) {
        ctx.error("Cannot find buildconfig ${buildConfigName}")
    }
    build = ctx.openshift.selector("buildconfig", buildConfigName).startBuild()

    def waitForLogs = 10
    def done = false
    for (i = 0; i < 10 && !done; i++) {
        try {
            build.logs("-f")
            done=true
        } catch (e) {
            // Most likely the logs command timed out
            ctx.echo "Retrying ${build.name()} logs in ${waitForLogs} seconds"
            ctx.sleep waitForLogs
            waitForLogs *= 1.3
            continue
        }
    }
    build.watch {
        def phase = it.object().status.phase
        if (phase == "New" || phase == "Pending" || phase == "Running") {
            return false
        }
        return true
    }
    if (build.object().status.phase != "Complete") {
        ctx.error("Build ${build.name()} failed.")
    }
  }
  return build
}