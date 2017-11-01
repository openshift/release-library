#!/usr/bin/groovy

def call(Object ctx, Object selector, boolean failFast, int timeOutSecs=7200) {
  ctx.openshift.withCluster() {
    def builds = ctx.openshift.selector("builds", selector)
    def names = builds.names()
    if (names.size() == 0) {
      ctx.error("no builds to wait for")
      return
    }

    ctx.echo "Waiting for builds ${names} to finish"
    ctx.timeout(time: timeOutSecs, unit: 'SECONDS') {
      def failed = false
      def failMsg
      builds.untilEach(names.size()) {
        if (failed) {
          return true
        }
        def phase = it.object().status.phase
        if (failFast && (phase == "Error" || phase == "Failed")) {
          failed = true
          failMsg = "Build ${it.object().metadata.name} has failed with phase ${phase}"
        }
        return phase != "New" && phase != "Pending" && phase != "Running"
      }
      if (failed) {
        ctx.error "${failMsg}"
      }
    }
    def failedCount = 0
    builds.withEach {
      def phase = it.object().status.phase
      if (phase != "Complete") {
        failedCount++
      }
    }
    if (failedCount > 0) {
      ctx.error "Failed ${failedCount} out of ${names.size()}"
    }
  }
}
