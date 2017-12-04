#!/usr/bin/groovy

class BuildStep implements java.io.Serializable {
  String fromTag
  String toTag
  String dockerfile

  BuildStep(fromTag, toTag, dockerfile) {
    this.fromTag = fromTag
    this.toTag = toTag
    this.dockerfile = dockerfile
  }

  def ToTag() {
    return this.toTag
  }

  def LaunchBuild(ctx) {
    def dockerfile = """FROM ${ctx._buildName}:${this.fromTag}
${this.dockerfile}
"""
    ctx.launchBuild(ctx, this.toTag, dockerfile)
  }
}


def call(fromTag, toTag, dockerfile) {
  return new BuildStep(fromTag, toTag, dockerfile)
}