#!/usr/bin/groovy

@NonCPS
def invokeProcess(Object ctx, Object args) {
  return ctx.openshift.process(*args)
}

def call(Object ctx, String path, Object... params) {
  def args = ["-f", path]
  for (p in params) {
    args.add("-p")
    args.add(p)
  }

  ctx.openshift.withCluster() {
    def objects = invokeProcess(ctx, args)
    ctx.openshift.apply(objects)
  }
}
