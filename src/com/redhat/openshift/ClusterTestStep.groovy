package com.redhat.openshift

import static com.redhat.openshift.OpenShiftUtilities.WaitFor

class ClusterTestStep implements Serializable, TestStep {
  PipelineImageTestStep step

  @Override
  void Run(Object ctx, Object env, ReadOnlyInfoCache info) {
    try {
      new DeployClusterStep().Run(ctx, env, info)

      WaitFor(ctx, "Secret", info.BuildName())
      ctx.echo "ClusterTest: running tests against a cluster"
      step.secretVolumes.put(info.BuildName(), "/var/secrets/kubeconfig")
      step.env.put("KUBECONFIG", "/var/secrets/kubeconfig/admin.kubeconfig")
      step.secretVolumes.put("gce-provisioner", "/var/secrets/gce-provisioner")
      step.env.put("GOOGLE_APPLICATION_CREDENTIALS", "/var/secrets/gce-provisioner/gce.json")
      step.Run(ctx, env, info)
      ctx.echo "ClusterTest: ran tests against a cluster"
    } finally {
      new DeprovisionClusterStep().Run(ctx, env, info)
    }
  }
}