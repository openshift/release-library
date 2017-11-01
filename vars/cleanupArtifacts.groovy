#!/usr/bin/groovy

def call(Object ctx, Object selector, int timeOutSecs=300) {
  try {
    ctx.openshift.withCluster() {
      def builds = ctx.openshift.selector("builds", selector)
      def pods = ctx.openshift.selector("pods", selector)
      def workingDir = ctx.pwd()
      def artifactDir = "${workingDir}/artifacts"

      def hasArtifacts = false

      try {
        if (builds.count() > 0) {
          hasArtifacts = true
          saveArtifacts(ctx, artifactDir, builds)
          builds.delete()
        }
      } catch (ee) {
        ctx.echo "Error cleaning up builds: ${ee}"
      }

      try {
        if (pods.count() > 0) {
          hasArtifacts = true
          saveArtifacts(ctx, artifactDir, pods)
          pods.delete()
        }
      } catch (ee) {
        ctx.echo "Error cleaning up pods: ${ee}"
      }

      if (hasArtifacts) {
        try {
          uploadArtifacts(ctx, artifactDir)
        } catch (ee) {
          ctx.echo "Error uploading artifacts"
        }
      }
    }
  } catch (e) {
    ctx.echo "Error cleaning up artifacts: ${e}"
  }
}
