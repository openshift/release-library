package com.redhat.openshift

import static com.redhat.openshift.OpenShiftUtilities.WaitForImageStreamTag

class WaitForPipelineImageReferenceStep implements Serializable, TestStep {
  PipelineImageReference reference

  @Override
  void Run(Object ctx, Object env, ReadOnlyInfoCache info) {
    ctx.echo "WaitForPipelineImageReference: waiting for pipeline cache ImageStreamTag ${this.reference.tag}"
    ctx.openshift.withCluster() {
      def imageStream = ctx.openshift.selector("imagestream", info.BuildName())
      WaitForImageStreamTag(ctx, imageStream, this.reference.tag)
    }
  }
}