#!/usr/bin/groovy

def call(Object ctx, String buildName, Object createFn, int timeOutSecs = 3600) {
  ctx.openshift.withCluster() {
    if (buildName != "") {
      def build = buildSelector(ctx, buildName)
      def buildObject = null
      try {
        buildObject = build.object()
      } catch (e) {
        buildObject = null
      }
      if (buildObject != null) {
        deleteBuild(ctx, buildName)
      }
    }
    result = createFn()
    if (buildName == "") {
      buildName = result.narrow("builds").name()
      ctx.echo "Created build ${buildName}"
    }
    ctx.echo "Waiting for build ${buildName}"
    waitForBuild(ctx, buildName, timeOutSecs)
  }
}
