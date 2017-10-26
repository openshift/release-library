#!/usr/bin/groovy
def call(Object ctx, String artifactDir, Object selector) {
  ctx.sh "mkdir -p ${artifactDir}"
  ctx.openshift.withCluster() {
    selector.withEach() {
      def name = it.name()
      def objectName = name.tokenize("/")[1]
      def objectType = name.tokenize("/")[0]
      ctx.sh "oc get ${name} -o yaml > ${artifactDir}/${objectName}.yaml"
      ctx.sh "oc describe ${name} > ${artifactDir}/${objectName}.description"
      if (objectType == "pods") {
        def pod = it.object()
        for (def i = 0; i < pod.spec.initContainers.size(); i++) {
          def containerName = pod.spec.initContainers[i].name
          ctx.sh "oc logs ${name} -c ${containerName} > ${artifactDir}/${objectName}_${containerName}.log"
        }
        for (def i = 0; i < pod.spec.containers.size(); i++) {
          def containerName = pod.spec.containers[i].name
          ctx.sh "oc logs ${name} -c ${containerName} > ${artifactDir}/${objectName}_${containerName}.log"
        }
      } else if (objectType == "build") {
        ctx.sh "oc logs ${name} > ${artifactDir}/${objectName}.log"
      }
    }
  }
}
