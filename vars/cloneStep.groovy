#!/usr/bin/groovy

class CloneStep implements java.io.Serializable {
  String baseImage

  CloneStep(baseImage) {
    this.baseImage = baseImage
  }

  def ToTag() {
    return "src"
  }

  def LaunchBuild(ctx, org, repo, pullRefs) {
    def dockerfile = """FROM ${this.baseImage}
ENV REPO_OWNER=${org} REPO_NAME=${repo} PULL_REFS=${pullRefs} GIT_COMMITTER_NAME=developer GIT_COMMITTER_EMAIL=developer@redhat.com
RUN umask 0002 && /usr/bin/release-ci cloneref --src-root=/data
WORKDIR /data/src/github.com/${org}/${repo}/
"""

    ctx.launchBuild(ctx, "src", dockerfile)
  }
}

def call(baseImage) {
  return new CloneStep(baseImage)
}