#!/usr/bin/groovy

class BuildStep implements java.io.Serializable {
  String name
  String fromTag
  String toTag
  String dockerfile

  BuildStep(name, fromTag, toTag, dockerfile) {
    this.name = name
    this.fromTag = fromTag
    this.toTag = toTag
    this.dockerfile = dockerfile
  }

  def Name() {
    return this.name
  }

  def ToTag() {
    return this.toTag
  }

  def LaunchBuild(ctx, buildName, jobId) {
    def dockerfile = """FROM ${buildName}:${this.fromTag}
${this.dockerfile}
"""
    ctx.launchBuild(ctx, this.name, jobId, buildName, this.toTag, dockerfile)
  }
}


def call(name, fromTag, toTag, dockerfile) {
  return new BuildStep(name, fromTag, toTag, dockerfile)
}