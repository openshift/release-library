package com.redhat.openshift

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

import static com.redhat.openshift.OpenShiftUtilities.*
import static com.redhat.openshift.TestUtilities.RELEASE_CI_PATH

class BuildUtilities implements Serializable {
  /**
   * EnsureImageStream ensures that the build ImageStream exists.
   */
  static void EnsureImageStream(Object ctx, ReadOnlyInfoCache info) {
    ctx.echo "EnsureImageStream: ensuring ImageStream ${info.BuildName()} exists"
    ctx.openshift.withCluster() {
      ctx.openshift.apply([
        "kind"    : "ImageStream",
        "metadata": [
          "name"  : info.BuildName(),
          "labels": [
            (CREATED_BY_CI_LABEL): CREATED_BY_CI_VALUE,
            (PERSISTS_LABEL)     : PERSISTS_VALUE,
            (JOB_ID_LABEL)       : info.JobID(),
            (BUILD_LABEL)        : info.BuildName()
          ]
        ]
      ])
    }
    ctx.echo "EnsureImageStream: ensured ImageStream ${info.BuildName()} exists"
  }

  /**
   * BuildLiteralDockerfile launches a build to create the toTag
   * on the build ImageStream if that ImageStreamTag does not yet
   * exist.
   */
  static String BuildLiteralDockerfile(Object ctx, ReadOnlyInfoCache info, String toTag, String dockerfile) {
    if (Exists(ctx, "ImageStreamTag", String.format("%s:%s", info.BuildName(), toTag))) {
      ctx.echo "BuildLiteralDockerfile: not launching Build to create ${toTag}, ImageStreamTag exists"
      return ""
    }
    ctx.echo "BuildLiteralDockerfile: launching Build to create ${toTag}"
    def build
    ctx.openshift.withCluster() {
      String output = ctx.openshift.raw("new-build",
        "--dockerfile", "\"${dockerfile}\"",
        "--to", "${info.BuildName()}:${toTag}",
        "--allow-missing-imagestream-tags",
        "--output", "json", "--dry-run",
      ).out
      build = BuildFromBuildConfig(ctx, info, toTag, output)
      RunConcurrentBuild(ctx, build)
    }
    ctx.echo "BuildLiteralDockerfile: launched Build to create ${toTag}"
    return build.metadata.name
  }

  static String BuildFromBuildConfig(Object ctx, ReadOnlyInfoCache info, String jobName, String listJSON) {
    ctx.echo "BuildFromBuildConfig: converting generated item list into a Build"
    def list = new JsonSlurperClassic().parseText(listJSON)
    def buildSpec
    for (item in list.items) {
      if (item.kind == "BuildConfig") {
        buildSpec = item.spec
        break
      }
    }
    buildSpec.triggers = new HashMap<>()
    buildSpec.serviceAccount = "builder"
    buildSpec.strategy.dockerStrategy.forcePull = true
    buildSpec.strategy.dockerStrategy.noCache = true
    def build = [
      "kind"    : "Build",
      "metadata": [
        "name"  : "${jobName}-${info.BuildName()}",
        "labels": [
          "job"           : jobName,
          (JOB_ID_LABEL)  : info.JobID(),
          (BUILD_LABEL)   : info.BuildName(),
          (PERSISTS_LABEL): DOESNT_PERSIST_VALUE
        ]
      ],
      "spec"    : buildSpec
    ]
    ctx.echo "BuildFromBuildConfig: converted generated item list into a Build"
    return build
  }

  static void RunConcurrentBuild(Object ctx, Object build) {
    ctx.echo "RunConcurrentBuild: running concurrent Build"
    String buildJSON = JsonOutput.prettyPrint(JsonOutput.toJson(build))
    ctx.writeFile file: "build.json", text: buildJSON
    ctx.sh "${RELEASE_CI_PATH} run-build -f build.json"
    ctx.echo "RunConcurrentBuild: ran concurrent Build"
  }

  static String BuildRepoDockerfile(Object ctx, ReadOnlyInfoCache info, String fromTag, String toTag, String contextDir) {
    if (Exists(ctx, "ImageStreamTag", String.format("%s:%s", info.BuildName(), toTag))) {
      ctx.echo "BuildRepoDockerfile: not launching Build to create ${toTag}, ImageStreamTag exists"
      return ""
    }
    ctx.echo "BuildRepoDockerfile: creating build of ${toTag} from ${fromTag} using Dockerfile under ${contextDir}"
    def build
    ctx.openshift.withCluster() {
      String sourceImage = String.format("%s:%s", info.BuildName(), BuildPipelineConfiguration.SOURCE_TAG)
      String contextDirPrefix = ctx.openshift.selector("imagestreamtag", sourceImage).object().image.dockerImageMetadata.ContainerConfig.WorkingDir
      def output = JsonOutput.toJson([
        "items": [[
                    "kind": "BuildConfig",
                    "spec": [
                      "runPolicy": "Serial",
                      "source"   : [
                        "type"      : "Image",
                        "images"    : [[
                                         "from" : [
                                           "kind": "ImageStreamTag",
                                           "name": sourceImage
                                         ],
                                         "paths": [[
                                                     "destinationDir": ".",
                                                     "sourcePath"    : "${contextDirPrefix}/${contextDir}"
                                                   ]],
                                       ]],
                        "contextDir": contextDir.tokenize('/').last(),
                      ],
                      "output"   : [
                        "to": [
                          "kind": "ImageStreamTag",
                          "name": "${info.BuildName()}:${toTag}"
                        ],
                      ],
                      "strategy" : [
                        "type"          : "Docker",
                        "dockerStrategy": [
                          "dockerfilePath": "Dockerfile",
                          "from"          : [
                            "kind": "ImageStreamTag",
                            "name": "${info.BuildName()}:${fromTag}"
                          ]
                        ]
                      ]
                    ]
                  ]]
      ])
      build = BuildFromBuildConfig(ctx, info, toTag, output)
      RunConcurrentBuild(ctx, build)
    }
    ctx.echo "BuildRepoDockerfile: created build of ${toTag} from ${fromTag} using Dockerfile under ${contextDir}"
    return build.metadata.name
  }

  /**
   * WaitForBuilds waits for builds selected by name
   * to finish and will error if any are not successful.
   */
  static void WaitForBuilds(Object ctx, List<String> names) {
    ctx.echo "WaitForBuilds: waiting for Builds: ${names}"
    ctx.openshift.withCluster() {
      def selector = ctx.openshift.selector("build", names[0])
      if (names.size() > 1) {
        for (String name : names[1..-1]) {
          selector = selector.union(ctx.openshift.selector("build", name))
        }
      }
      Map<String, String> watchLabel = new HashMap<>()
      watchLabel.put("origin-ci-watch-group", UUID.randomUUID().toString())
      selector.label(watchLabel, "--overwrite")
      ctx.openshift.selector("builds", watchLabel).untilEach(1) {
        def build = it.object()
        String phase = build.status.phase
        ctx.echo "WaitForBuilds: Build ${build.metadata.name} is in phase ${phase}"
        if (phase == "Complete") {
          return true
        } else if (phase == "Failed" || phase == "Error" || phase == "Cancelled") {
          ctx.error "WaitForBuilds: Build ${build.metadata.name} finished with phase ${phase}"
        }
      }
    }
    ctx.echo "WaitForBuilds: all Builds finished"
  }
}