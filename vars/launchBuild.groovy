#!/usr/bin/groovy

def call(ctx, toTag, dockerfile) {
  ctx.openshift.withCluster() {
    def output = ctx.openshift.raw("new-build",
      "--dockerfile", "'${dockerfile}'",
      "--to", "${ctx._buildName}:${toTag}",
      "--output", "json", "--dry-run",
    ).out
    output = new groovy.json.JsonSlurperClassic().parseText(output)
    def buildSpec = [:]
    for(item in output.items) {
      if(item.kind == "BuildConfig") {
        buildSpec = item.spec
        break
      }
    }
    buildSpec.triggers = [:]
    buildSpec.strategy.dockerStrategy.forcePull = true
    buildSpec.strategy.dockerStrategy.noCache = true
    def build = [
      "kind": "Build",
      "metadata": [
        "name": "${toTag}-${buildName}",
        "labels": [
          "job": "${toTag}",
          "job-id": "${ctx._jobId}",
          "build": "${ctx._buildName}",
        ]
      ],
      "spec": buildSpec
    ]
    def buildJSON = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(build))
    ctx.runConcurrentBuild(ctx, buildJSON)
  }
}
