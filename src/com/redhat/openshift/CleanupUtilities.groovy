package com.redhat.openshift

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

import static com.redhat.openshift.GCSUtilities.*
import static com.redhat.openshift.OpenShiftUtilities.*

class CleanupUtilities implements Serializable {
  static void CleanupArtifacts(Object ctx, Object env, ReadOnlyInfoCache info) {
    // We want to capture artifacts from the job -- this will be a superset of the
    // artifacts that this specific build may have created as we may be re-using
    // output from a previous run of the job that had the same base configuration.
    Map<String, String> selector = [(BUILD_LABEL): info.BuildName()]
    ctx.echo "CleanupArtifacts: cleaning up artifacts matching selector ${selector}"
    try {
      SaveObjectArtifacts(ctx, selector)
    } catch (saveException) {
      ctx.echo "CleanupArtifacts: failed to save artifacts: ${saveException}"
    }

    try {
      GenerateFinishedMetadata(ctx)
    } catch (generateException) {
      ctx.echo "CleanupArtifacts: failed to generate finished metadata: ${generateException}"
    }

    try {
      UploadArtifacts(ctx, env)
    } catch (uploadException) {
      ctx.echo "CleanupArtifacts: failed to upload artifacts: ${uploadException}"
    }

    try {
      ctx.openshift.withCluster() {
        // We want to clean up artifacts from this specific build that
        // are not persistent-- this will omit things like images that
        // are shared between builds of this job on the same code.
        Map<String, String> cleanupSelector = [
          (BUILD_LABEL)   : info.BuildName(),
          (JOB_ID_LABEL)  : info.JobID(),
          (PERSISTS_LABEL): DOESNT_PERSIST_VALUE
        ]
        ctx.echo "CleanupArtifacts: cleaning up objects matching selector ${cleanupSelector}"
        ctx.echo "CleanupArtifacts: will delete ${ctx.openshift.selector("all", cleanupSelector).names()}"
        ctx.openshift.selector("all", cleanupSelector).delete()
      }
    } catch (cleanupException) {
      ctx.echo "CleanupArtifacts: failed to clean up objects: ${cleanupException}"
    }
    ctx.echo "CleanupArtifacts: cleaned up artifacts"
  }

  private static final List<String> loggableTypes = Arrays.asList("Build", "BuildConfig", "DeploymentConfig")

  /**
   * SaveObjectArtifacts will save the object's description,
   * configuration and logs in a best-effort way. Errors in
   * gathering or serializing any one item should not cause
   * any other information from not being handled.
   */
  static void SaveObjectArtifacts(Object ctx, Map<String, String> selector) {
    ctx.echo "SaveObjectArtifacts: saving artifacts for objects matched by selector ${selector}"
    Map<String, String> artifacts = new HashMap<>()
    ctx.openshift.withCluster() {
      ctx.openshift.selector("all", selector).withEach {

        String type = it.object().kind
        String name = it.object().metadata.name
        String prefix = String.format("%s/%s/%s", ARTIFACT_DIR, type, name)
        ctx.echo "SaveObjectArtifacts: saving artifacts for ${type} ${name}"

        artifacts.putAll(getObjectInfo(ctx, it, prefix))
        if (type == "Pod") {
          ctx.echo "SaveObjectArtifacts: saving logs for all containers in ${type} ${name}"
          artifacts.putAll(getPodContainerLogs(ctx, it, prefix))
        }

        if (type == "Build") {
          def build = it.object()
          String buildPodName = null
          if (build.metadata != null && build.metadata.annotations != null) {
            buildPodName = build.metadata.annotations.get('openshift.io/build.pod-name', null)
          }
          if (buildPodName != null) {
            ctx.echo "SaveObjectArtifacts: saving logs for Pod ${buildPodName} for Build ${name}"
            def buildPodSelector = ctx.openshift.selector("pod", buildPodName)
            String buildPodPrefix = String.format("Pod/%s", buildPodName)
            artifacts.putAll(getObjectInfo(ctx, buildPodSelector, buildPodPrefix))
            artifacts.putAll(getPodContainerLogs(ctx, buildPodSelector, buildPodPrefix))
          }
        }

        if (loggableTypes.contains(type)) {
          try {
            ctx.echo "SaveObjectArtifacts: saving logs for ${type} ${name}"
            String log = ctx.openshift.raw("logs", it.name()).out
            artifacts.put(String.format("%s/output.txt", prefix), log)
          } catch (logException) {
            ctx.echo "SaveObjectArtifacts: failed to gather logs for ${it.name()}: ${logException}"
          }
        }

      }
    }

    // TODO: this leaves out the end of the Jenkins log, which often has the stacktrace...
    artifacts.put("jenkins-log.txt", ctx.currentBuild.rawBuild.log.replaceAll(".\\[[0-9]+m.*.\\[[0-9]+m", ""))
    if (!artifacts.containsKey(BUILD_LOG)) {
      // no pod failed so we need to upload
      // the Jenkins log as the build log
      artifacts.put(BUILD_LOG, artifacts.get("jenkins-log.txt"))
    }

    ctx.dir(GCSDir(ctx)) {
      for (String fileName : artifacts.keySet()) {
        try {
          if (ctx.fileExists(file: fileName)) {
            // TODO: figure out what to do here, collisions probable on build-log.txt
            ctx.echo "SaveObjectArtifacts: not over-writing ${fileName}"
            continue
          }
          ctx.writeFile file: fileName, text: artifacts.get(fileName)
          ctx.echo "SaveObjectArtifacts: wrote ${fileName}"
        } catch (writeException) {
          ctx.echo "SaveObjectArtifacts: failed to write file ${fileName}: ${writeException}"
        }
      }
    }
    ctx.echo "SaveObjectArtifacts: saved artifacts for objects matched by selector ${selector}"
  }

  private static Map<String, String> getObjectInfo(Object ctx, Object selector, String prefix) {
    Map<String, String> artifacts = new HashMap<>()
    try {
      String configuration = ctx.openshift.raw("get", selector.name(), "--output", "yaml").out
      artifacts.put(String.format("%s/configuration.yaml", prefix), configuration)
    } catch (configException) {
      ctx.echo "SaveObjectArtifacts: failed to gather configuration for ${selector.name()}: ${configException}"
    }

    try {
      String description = ctx.openshift.raw("describe", selector.name()).out
      artifacts.put(String.format("%s/description.txt", prefix), description)
    } catch (describeException) {
      ctx.echo "SaveObjectArtifacts: failed to gather description for ${selector.name()}: ${describeException}"
    }
    return artifacts
  }

  private static Map<String, String> getPodContainerLogs(Object ctx, Object podSelector, String prefix) {
    Map<String, String> artifacts = new HashMap<>()
    Object pod = podSelector.object()

    List<String> containerNames = new ArrayList<>()
    if (pod.spec.initContainers != null) {
      for (Object initContainer : pod.spec.initContainers) {
        containerNames.add(initContainer.name)
      }
    }
    for (Object container : pod.spec.containers) {
      containerNames.add(container.name)
    }

    List<String> failedContainerNames = new ArrayList<>()
    if (pod.status.initContainerStatuses != null) {
      for (Object status : pod.status.initContainerStatuses) {
        if (status.state.containsKey("terminated") && status.state.terminated.exitCode != 0) {
          failedContainerNames.add(status.name)
        }
      }
    }
    for (Object status : pod.status.containerStatuses) {
      if (status.state.containsKey("terminated") && status.state.terminated.exitCode != 0) {
        failedContainerNames.add(status.name)
      }
    }

    String name = pod.metadata.name
    for (String containerName : containerNames) {
      try {
        ctx.echo "getPodContainerLogs: gathering logs for Pod ${name} Container ${containerName}"
        String log = ctx.openshift.raw("logs", "pod/${name}", "--container", containerName).out
        artifacts.put(String.format("%s/%s.log", prefix, containerName), log)
        if (failedContainerNames.contains(containerName)) {
          String buildLogPrefix
          if (artifacts.containsKey(BUILD_LOG)) {
            buildLogPrefix = String.format("%s\n\n", artifacts.get(BUILD_LOG))
          } else {
            buildLogPrefix = ""
          }
          artifacts.put(BUILD_LOG, String.format("%sContainer %s in Pod %s failed:\n%s", buildLogPrefix, containerName, name))
        }
      } catch (logException) {
        ctx.echo "getPodContainerLogs: failed to gather logs for Pod ${name} Container ${containerName}: ${logException}"
      }
    }
    return artifacts
  }

  static void DeleteWorkspace(Object ctx) {
    ctx.echo "DeleteWorkspace: deleting workspace"
    try {
      ctx.deleteDir()
    } catch (deleteException) {
      ctx.echo "deleteWorkspace: error deleting workspace: ${deleteException}"
    }
    ctx.echo "DeleteWorkspace: deleted workspace"
  }

  static void PruneResources(Object ctx) {
    ctx.echo "PruneResources: removing old persistent resources"
    ctx.openshift.withCluster() {
      List<String> objectTypes = Arrays.asList("all", "configmaps", "secrets")
      for (String type : objectTypes) {
        ctx.openshift.selector(type, [(CREATED_BY_CI_LABEL): CREATED_BY_CI_VALUE, (PERSISTS_LABEL): PERSISTS_VALUE]).withEach {
          if (!it.exists()) {
            // cascading deletes mean that we may have
            // selected an object that doesn't exist by
            // the time we go to delete it
            return
          }
          String formattedCreationTimestamp = it.object().metadata.creationTimestamp
          if (olderThanCutoff(formattedCreationTimestamp)) {
            try {
              ctx.echo "PruneResources: removing ${it.name()} from ${formattedCreationTimestamp}"
              it.delete()
            } catch (removalException) {
              ctx.echo "PruneResources: failed to remove ${it.name()}: ${removalException}"
            }
          }
        }
      }
    }
    ctx.echo "PruneResources: removed old persistent resources"
  }

  @NonCPS
  private static boolean olderThanCutoff(String timestamp) {
    Instant cutoff = Instant.now().minus(Duration.ofDays(1))
    DateTimeFormatter parser = DateTimeFormatter.ISO_INSTANT
    Instant instant = Instant.from(parser.parse(timestamp))
    return instant.isBefore(cutoff)
  }
}