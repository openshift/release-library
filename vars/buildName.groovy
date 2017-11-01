#!/usr/bin/groovy

def call(Object ctx) {
  def hash
  ctx.withEnv([
    "REPO_NAME=${params.REPO_NAME}", 
    "REPO_OWNER=${params.REPO_OWNER}"
  ]) {
    hash = ctx.sh(script: "${releaseCIPath()} refhash ${params.PULL_REFS}", returnStdout: true).trim()
  }
  ctx.echo "Build name is ${hash}"
  return hash
}
