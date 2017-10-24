#!/usr/bin/groovy
def call(Object ctx, String artifactDir, Object selector) {
  ctx.sh "mkdir -p ${artifactDir}"
  ctx.openshift.withCluster() {
    selector.withEach() {
      def name = it.name()
      def objectName = name.tokenize("/")[1]
      ctx.sh "oc get ${name} -o yaml > ${artifactDir}/${objectName}.yaml"
      ctx.sh "oc describe ${name} > ${artifactDir}/${objectName}.description"
      if (name.startsWith("pod/")) {
        def pod = it.object()
        for (def i = 0; i < pod.spec.containers.size(); i++) {
          def containerName = pod.spec.containers[i].name
          ctx.sh "oc logs ${name} -c ${containerName} > ${artifactDir}/${objectName}_${containerName}.log"
        }
      } else if (name.startsWith("build/")) {
        ctx.sh "oc logs ${name} > ${artifactDir}/${objectName}.log"
      }
    }
  }
}
