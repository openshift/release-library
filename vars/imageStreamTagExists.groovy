#!/usr/bin/env groovy

def tagExists(Object ctx, String imageStreamName, String tag) {
    def imageStream
    try {
        imageStream = ctx.openshift.selector('is', imageStreamName).object()
    } catch(e) {
        return false
    }
    if (imageStream == null) {
        return false
    }
    def tags = imageStream.status.tags
    if (tags == null) {
        return false
    }
    for (i = 0; i < tags.size(); i++) {
        if (tags[i].tag == tag) {
            if (tags[i].items.size() > 0) {
                return true
            }
        }
    }
    return false
}


def call(Object ctx, String imageStreamName, String tag) {
  def exists = false
  ctx.openshift.withCluster() {
    exists = tagExists(ctx, imageStreamName, tag)
  }
  return exists
}
