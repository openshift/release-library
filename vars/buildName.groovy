#!/usr/bin/groovy

def call(Object ctx) {
  def hash
  hash = ctx.sh(script: "release-ci refhash ${params.PULL_REFS}", returnStdout: true)
  return hash
}
