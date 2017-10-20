#!/usr/bin/groovy

def call(Object ctx, String buildName, int timeOutSecs = 600) {
  ctx.openshift.withCluster() {
    def build = buildSelector(ctx, buildName)
    if (!build.exists()) {
      ctx.error("build ${buildName} doesn't exist")
    }

    ctx.echo "Waiting for build ${buildName} to finish"
    def waitForLogs = 10
    def done = false
    ctx.echo "Obtaining build logs for ${buildName}"
    ctx.timeout(time: timeOutSecs, unit: 'SECONDS') {
      while(!done) {
        if (!build.exists()) {
          ctx.error("Build ${buildName} no longer exists")
          return
        }
        def phase = build.object().status.phase
        if (phase != "New" && phase != "Pending" && phase != "Running") {
          // build has completed, exit this loop
          return
        }
        try {
            build.logs("-f")
            done=true
        } catch (e) {
            // Most likely the logs command timed out
            ctx.echo "Retrying ${build.name()} logs in ${waitForLogs} seconds"
            ctx.sleep waitForLogs
            waitForLogs = (waitForLogs * 12)/10
            continue
        }
      }
    }
    ctx.echo "Ensuring build status is complete for ${buildName}"
    ctx.timeout(time: timeOutSecs, unit: 'SECONDS') {
      build.watch {
          def phase = it.object().status.phase
          if (phase == "New" || phase == "Pending" || phase == "Running") {
              return false
          }
          return true
      }
    }
    if (build.object().status.phase != "Complete") {
      ctx.error("build ${buildName} did not complete successfully (${build.object().status.phase})")
    }
  }
}
