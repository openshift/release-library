#!/usr/bin/groovy

def call(ctx, jobName, jobId, buildName, toTag, dockerfile) {
  ctx.openshift.withCluster() {
    def output = ctx.openshift.raw("new-build",
      "--dockerfile", "'${dockerfile}'",
      "--to", "${buildName}:${toTag}",
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
        "name": "${jobName}-${jobId}",
        "labels": [
          "job": "${jobName}",
          "job-id": "${jobId}",
          "build": "${buildName}",
        ]
      ],
      "spec": buildSpec
    ]
    ctx.runAndWaitForBuild(ctx,
      build.metadata.name,
      { return ctx.openshift.apply(build) }
    )
  }
}