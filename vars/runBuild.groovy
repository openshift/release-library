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
    echo "Started build for ${buildConfigName}: ${build.name()}"

    def waitForLogs = 10
    def done = false
    for (i = 0; i < 10 && !done; i++) {
        try {
            build.logs("-f")
            done=true
        } catch (e) {
            def phase = build.object().status.phase
            if (phase != "New" && phase != "Pending" && phase != "Running") {
              // build has completed, exit this loop
              done=true
            } else {
              // Most likely the logs command timed out
              ctx.echo "Retrying ${build.name()} logs in ${waitForLogs} seconds"
              ctx.sleep waitForLogs
              waitForLogs = (waitForLogs * 13)/10
            }
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
