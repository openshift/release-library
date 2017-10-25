#!/usr/bin/groovy

def call(Object ctx) {
  def hash
  def originURL
  def originURLFlag
  try {
    originURL = "${ctx.params.ORIGIN_URL}"
  } catch (e) {
    originURL = ""
  }
  if (originURL) {
    originURLFlag = "--source-url=${originURL}"
  }
  hash = ctx.sh(script: "${releaseCIPath()} refhash ${originURLFlag} ${params.PULL_REFS}", returnStdout: true).trim()
  return hash
}
