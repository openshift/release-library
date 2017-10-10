#!/usr/bin/groovy
def call(Object ctx, resourceType, selector, filterFn) {
  ctx.echo "Filtering objects of type ${resourceType} with selector ${selector}"
  def selectedObjects = []
  ctx.openshift.withCluster() {
    def builds = ctx.openshift.selector(resourceType, selector)
    ctx.echo "builds = ${builds}"
    builds.withEach({
      def obj = it.object()
      result = filterFn(obj)
      if (result) {
        selectedObjects.add(obj)
      }
    })
  }
  return selectedObjects
}