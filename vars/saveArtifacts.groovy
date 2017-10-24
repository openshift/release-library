#!/usr/bin/groovy
def call(Object ctx, String artifactDir, Object selector) {
  ctx.sh "mkdir -p ${artifactDir}"
  ctx.openshift.withCluster() {
    selector.withEach() {
      def name = it.name()
      ctx.sh "oc get ${name} -o yaml > ${artifactDir}/${name}.yaml"
      ctx.sh "oc describe ${name} > ${artifactDir}/${name}.description"
      if (name.startsWith("pod/")) {
        def pod = it.object()
        for (def i = 0; i < pod.spec.containers.size(); i++) {
          def containerName = pod.spec.containers[i].name
          ctx.sh "oc logs ${name} -c ${containerName} > ${artifactDir}/${name}_${containerName}.log"
        }
      }
    }
  }
}
