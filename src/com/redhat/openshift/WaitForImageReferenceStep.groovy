package com.redhat.openshift

import static com.redhat.openshift.OpenShiftUtilities.WaitForImageStreamTag

class WaitForImageReferenceStep implements Serializable, TestStep {
  ImageReference reference

  @Override
  void Run(Object ctx, Object env, ReadOnlyInfoCache info) {
    ctx.echo "WaitForImageReference: waiting for ImageStreamTag ${this.reference.name}:${this.reference.tag} in Namespace ${this.reference.namespace}"
    ctx.openshift.withCluster() {
      ctx.openshift.withProject(this.reference.namespace) {
        def imageStream = ctx.openshift.selector("imagestream", this.reference.name)
        WaitForImageStreamTag(ctx, imageStream, this.reference.tag)
      }
    }
  }
}