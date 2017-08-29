#!/usr/bin/env groovy

def call(Object ctx, String imageStreamName, String tag) {
  def ref = null
  ctx.openshift.withCluster() {
    def imageStream = ctx.openshift.selector('is', imageStreamName)
    if (!imageStream.exists()) {
      ctx.error("image stream ${imageStreamName} does not exist")
    }
    def tags = imageStream.object().status.tags
    if (tags == null) {
        ctx.error("image stream ${imageStreamName} does not have any tags")
        return null
    }
    for (i = 0; i < tags.size(); i++) {
        if (tags[i].tag == tag) {
            if (tags[i].items.size() > 0) {
                ref = tags[i].items[0].dockerImageReference
                break
            }
        }
    }
  }
  if (ref == null) {
    ctx.error("image stream tag ${imageStreamName}:${tag} not found")
  }
  return ref
}
