package com.redhat.openshift

class BaseImageTagStep implements Serializable, Step {
  ImageReference baseImage

  @Override
  List<String> Run(Object ctx, Object env, ReadOnlyInfoCache info) {
    ctx.openshift.withCluster() {
      ctx.openshift.tag(this.baseImage.PullSpec(), String.format("%s:%s", info.BuildName(), this.baseImage.name))
    }
    return Collections.emptyList()
  }

  @Override
  List<String> From() {
    return Collections.singletonList(baseImage.PullSpec())
  }

  @Override
  String To() {
    return baseImage.name
  }

  @Override
  String ID() {
    return String.format("BaseImageTagStep: from %s, to %s", this.baseImage.PullSpec(), this.baseImage.name)
  }
}