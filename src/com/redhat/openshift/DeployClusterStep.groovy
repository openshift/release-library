package com.redhat.openshift

import static com.redhat.openshift.GCSUtilities.BUILD_LOG
import static com.redhat.openshift.GCSUtilities.GCSDir
import static com.redhat.openshift.OpenShiftUtilities.WaitFor
import static com.redhat.openshift.TestUtilities.GCE_DATA_PATH
import static com.redhat.openshift.TestUtilities.UniqueNodeName

class DeployClusterStep implements Serializable, TestStep {
  @Override
  void Run(Object ctx, Object env, ReadOnlyInfoCache info) {
    WaitFor(ctx, "ConfigMap", info.BuildName())

    ctx.echo "DeployCluster: deploying a test cluster for build ${info.BuildName()}"
    String imageBase
    String imageNamespace
    String rpmRepo
    ctx.openshift.withCluster() {
      def configMap = ctx.openshift.selector("configmap", "${info.BuildName()}").object()
      imageBase = configMap.data["image-base"]
      imageNamespace = configMap.data["namespace"]
      rpmRepo = configMap.data["rpm-repo"]
    }

    ctx.dir("cluster/test-deploy/data") {
      ctx.stash "data-files"
    }

    try {
      String name = UniqueNodeName(info, "cluster-deploy-step")
      ctx.podTemplate(
        cloud: "openshift",
        label: name,
        containers: [
          ctx.containerTemplate(
            name: name,
            image: "${imageBase}-gce:ci",
            ttyEnabled: true,
            command: "cat"
          )
        ],
        annotations: [
          ctx.podAnnotation(key: "alpha.image.policy.openshift.io/resolve-names", value: "*")
        ],
        volumes: [
          ctx.secretVolume(
            secretName: 'gce-provisioner',
            mountPath: '/var/secrets/gce-provisioner'
          ),
          ctx.secretVolume(
            secretName: 'gce-ssh',
            mountPath: '/var/secrets/gce-ssh'
          )
        ]
      ) {
        ctx.node(name) {
          ctx.container(name) {
            // we need to `dir{}` to a relative dir and copy as the
            // Kubernetes plugin does not work well with absolute
            // paths in multi-container pods
            ctx.dir("data-files") {
              ctx.unstash "data-files"
              ctx.sh "cp * ${GCE_DATA_PATH}"
            }
            ctx.sh "cp -L /var/secrets/gce-provisioner/*.json /var/secrets/gce-ssh/ssh* ${GCE_DATA_PATH}"
            String instancePrefix = info.BuildName().take(25)
            String script = $/cd $${WORK} && HOME=/home/cloud-user $${WORK}/entrypoint.sh env INSTANCE_PREFIX=${
              instancePrefix
            } ansible-playbook -vvv -e 'oreg_url=${imageBase}-$${component}:ci' -e "openshift_test_repo=${
              rpmRepo
            }" playbooks/launch.yaml/$
            String output
            Boolean failed = false
            try {
              try {
                ctx.sh "set -o pipefail; ${script} 2>&1 | tee /tmp/out"
              } catch (runException) {
                ctx.echo "DeployCluster: failed to deploy cluster: ${runException}"
                failed = true
                throw runException
              } finally {
                output = ctx.sh(script: "cat /tmp/out", returnStdout: true)
              }
              ctx.dir("kubeconfig") {
                ctx.sh "cp /tmp/admin.kubeconfig ."
                ctx.stash "kubeconfig"
              }
            } finally {
              ctx.dir("gcs") {
                String log = String.format("\$ %s\n%s\n", script, output)
                ctx.writeFile file: "deploy-log.txt", text: log
                if (failed) {
                  ctx.writeFile file: BUILD_LOG, text: log
                }
                ctx.stash name: "artifacts"
              }
            }
          }
        }
        ctx.dir("kubeconfig") {
          ctx.unstash "kubeconfig"
          ctx.openshift.withCluster() {
            ctx.openshift.raw(
              "create", "secret", "generic", info.BuildName(),
              "--from-file", "admin.kubeconfig=./admin.kubeconfig"
            )
            ctx.echo "DeployCluster: saved admin.kubeconfig in Secret ${info.BuildName()}"
          }
        }
      }
    } finally {
      ctx.echo "DeployCluster: unarchiving deploy artifacts"
      ctx.dir(GCSDir(ctx)) {
        ctx.unstash name: "artifacts"
      }
    }
    ctx.echo "DeployCluster: deployed a test cluster for build ${info.BuildName()}"
  }
}