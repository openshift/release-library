#!/usr/bin/groovy

def call(Object ctx, String imageStreamName, String tag, int timeOutSecs = 300) {
  def exists = false
  ctx.openshift.withCluster() {
    def imageStream = ctx.openshift.selector('is', imageStreamName)
    ctx.timeout(time: timeOutSecs, unit: 'SECONDS') {
      imageStream.watch {
        def tags = it.object().status.tags
        for (t in tags) {
          if (t.tag == tag && t.items != null && t.items.size() > 0) {
            return true
          }
        }
        return false
      }
    }
  }
  return false
}