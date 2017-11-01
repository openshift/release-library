#!/usr/bin/groovy

// runCommand will report if an error occurs but will not fail the build
def runCommand(ctx, cmd) {
  try {
    ctx.sh(script: cmd)
  } catch(e) {
    ctx.echo "error running ${cmd}: ${e}"
  }
}

def savePodContainerLogs(ctx, artifactDir, podSelector, podName, pod) {
  if (pod.spec.initContainers != null) {
    for (def i = 0; i < pod.spec.initContainers.size(); i++) {
      def containerName = pod.spec.initContainers[i].name
      runCommand(ctx, "oc logs ${podSelector} -c ${containerName} > ${artifactDir}/${podName}_${containerName}.log")
    }
  }
  for (def i = 0; i < pod.spec.containers.size(); i++) {
    def containerName = pod.spec.containers[i].name
    runCommand(ctx, "oc logs ${podSelector} -c ${containerName} > ${artifactDir}/${podName}_${containerName}.log")
  }
}

def call(Object ctx, String artifactDir, Object selector) {
  ctx.sh "mkdir -p ${artifactDir}"
  ctx.openshift.withCluster() {
    selector.withEach() {
      def name = it.name()
      def objectName = name.tokenize("/")[1]
      def objectType = name.tokenize("/")[0]
      runCommand(ctx, "oc get ${name} -o yaml > ${artifactDir}/${objectName}.yaml")
      runCommand(ctx, "oc describe ${name} > ${artifactDir}/${objectName}.description")
      if (objectType == "pod" || objectType == "pods") {
        def pod = it.object()
        savePodContainerLogs(ctx, artifactDir, name, objectName, pod)
      } else if (objectType == "build" || objectType == "builds") {
        runCommand(ctx, "oc logs ${name} > ${artifactDir}/${objectName}.log")
        def podName = it.object().metadata.annotations['openshift.io/build.pod-name']
        if (podName != null && podName != "") {
          def buildPod = ctx.openshift.selector("pod/${podName}")
          if (buildPod.exists()) {
            runCommand(ctx, "oc get pod/${podName} -o yaml > ${artifactDir}/${podName}.yaml")
            runCommand(ctx, "oc describe pod/${podName} > ${artifactDir}/${podName}.description")
            savePodContainerLogs(ctx, artifactDir, "pod/${podName}", podName, buildPod.object())
          }
        }
      }
    }
  }
}
