#!/usr/bin/groovy
def call(Object ctx, String imageStream, tag, name, value) {
  ctx.openshift.withCluster() {
    def is = ctx.openshift.selector("is", imageStream).object()
    if (is == null) {
        ctx.error("cannot find imagestream ${imageStream}")
        return
    }
    def tags = is.spec.tags
    def tagObj = null
    for (i = 0; i < tags.size(); i++) {
        if (tags[i].name == tag) {
           if (tags[i].annotations == null) {
               tags[i].annotations = [:]
           }
           tags[i].annotations[name] = value
           break
        }
    }
    ctx.openshift.apply(is)
  }
}
