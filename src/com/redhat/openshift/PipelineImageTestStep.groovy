package com.redhat.openshift

import static com.redhat.openshift.GCSUtilities.BUILD_LOG
import static com.redhat.openshift.GCSUtilities.GCSDir
import static com.redhat.openshift.TestUtilities.UniqueNodeName

class PipelineImageTestStep implements Serializable, TestStep {
  /**
   * tag is which pipeline image cache to run in
   */
  String tag

  /**
   * ram and cpu define the cpu and memory requests
   * for the pod running these tests. The memory limit
   * is set to equal the memory request.
   */
  String ram = "1Gi"
  String cpu = "1000m"

  /**
   * env configures the commands that run
   */
  Map<String, String> env = new HashMap<>()

  /**
   * commands execute the test
   */
  List<String> commands

  /**
   * artifactDir is where the commands will deposit
   * their artifacts. If unset, this will default under
   * the repository root to _output/scripts.
   */
  String artifactDir = "_output/scripts/"

  /**
   * secretVolumes holds a mapping of secret name to
   * mount path for secrets that should be available
   * to the test pod
   */
  Map<String, String> secretVolumes = new HashMap<>()

  @Override
  void Run(Object ctx, Object env, ReadOnlyInfoCache info) {
    TestStep prerequisite = new WaitForPipelineImageReferenceStep(reference: new PipelineImageReference(tag: this.tag))
    prerequisite.Run(ctx, env, info)

    String workingDir
    ctx.openshift.withCluster() {
      workingDir = ctx.openshift.selector("imagestreamtag", "${info.BuildName()}:${this.tag}").object().image.dockerImageMetadata.ContainerConfig.WorkingDir
    }
    ctx.echo "PipelineImageTestStep: executing test in container from image tag ${this.tag}"
    try {
      def envs = []
      if (!this.env.containsKey("TERM")) {
        this.env.put("TERM", "xterm-256-color")
      }
      for (String key : this.env.keySet()) {
        envs.add(ctx.envVar(key: key, value: this.env[key]))
      }

      def volumes = []
      for (String key: this.secretVolumes.keySet()) {
        volumes.add(ctx.secretVolume(secretName: key, mountPath: this.secretVolumes[key]))
      }

      String name = UniqueNodeName(info, "pipeline-image-test-step")
      // org.csanchez.jenkins.plugins.kubernetes.pipeline.PodTemplateStep
      ctx.podTemplate(
        cloud: "openshift",
        label: name,
        containers: [
          ctx.containerTemplate(
            name: name,
            image: "${info.BuildName()}:${this.tag}",
            ttyEnabled: true,
            command: "cat",
            envVars: envs,
            resourceRequestCpu: this.cpu,
            resourceRequestMemory: this.ram,
            resourceLimitMemory: this.ram,
          )
        ],
        annotations: [
          ctx.podAnnotation(key: "alpha.image.policy.openshift.io/resolve-names", value: "*")
        ],
        volumes: volumes,
      ) {
        ctx.node(name) {
          ctx.container(name) {
            Map<String, String> commandOutput = new LinkedHashMap<>()
            try {
              for (String command : this.commands) {
                try {
                  // Returning stdout doesn't seem to work when the command fails, which
                  // is basically the case where we care the most about getting both of
                  // out output file descriptors. So we just embed bash redirects into a
                  // shell step in our class in the pipeline library in the pipeline...
                  ctx.sh "set -o pipefail; cd ${workingDir} && ${command} 2>&1 | tee /tmp/out"
                } catch (runException) {
                  ctx.echo "PipelineImageTestStep: failed to run test command: ${runException}"
                  throw runException
                } finally {
                  // For whatever reason readFile here will error and say no file exists,
                  // but we are confident it does and can grab it's content with `cat`.
                  String output = ctx.sh(script: "cat /tmp/out", returnStdout: true)
                  commandOutput.put(command, output)
                }
              }
            } catch (runException) {
              ctx.echo "PipelineImageTestStep: failed to run test commands: ${runException}"
              throw runException
            } finally {
              ctx.dir("gcs") {
                ctx.sh "cp -R ${workingDir}/${this.artifactDir} artifacts"

                StringBuilder buildLog = new StringBuilder()
                for (String command : commandOutput.keySet()) {
                  buildLog.append(String.format("\$ %s\n%s\n", command, commandOutput[command]))
                }
                ctx.writeFile file: BUILD_LOG, text: buildLog.toString()

                ctx.stash name: "artifacts"
              }
            }
          }
        }
      }
    } catch (runException) {
      ctx.echo "PipelineImageTestStep: failed to run test: ${runException}"
      throw runException
    } finally {
      ctx.echo "PipelineImageTestStep: unarchiving test artifacts"
      ctx.dir(GCSDir(ctx)) {
        ctx.unstash name: "artifacts"
      }
    }
  }
}