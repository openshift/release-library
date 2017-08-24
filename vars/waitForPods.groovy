#!/usr/bin/groovy

def call(Object ctx, Object selector, int timeOutSecs=300) {
  ctx.openshift.withCluster() {
    def pods = ctx.openshift.selector("pods", selector)
    def names = pods.names()
    if (names.size() == 0) {
      ctx.error("no pods found matching selector ${selector}")
      return
    }
    ctx.timeout(time: timeOutSecs, unit: 'SECONDS') {
      pods.untilEach(names.size()) {
        def phase = it.object().status.phase
        return phase != "Pending" && phase != "Running" 
      }
    } 
    def failedCount = 0
    pods.withEach {
      def phase = it.object().status.phase
      if (phase != "Succeeded") {
        failedCount++
      }
    }
    if (failedCount > 0) {
      ctx.error "Failed ${failedCount} out of ${names.size()}"
    }
  }
}
