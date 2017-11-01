#!/usr/bin/groovy

def call(Object ctx, String deploymentConfigName, int timeOutSecs=600) {
  ctx.openshift.withCluster() {
    ctx.echo "Waiting for deployment config ${deploymentConfigName}"
    ctx.timeout(time: timeOutSecs, unit: 'SECONDS') {
      ctx.openshift.selector("dc", deploymentConfigName).rollout().status("-w")
    }
  }
}
