#!/usr/bin/groovy

def call(Object ctx, String serviceAccountName) {
  def sa 
  def secretName
  ctx.openshift.withCluster() {
    try {
      sa = ctx.openshift.selector("sa", serviceAccountName).object()
    } catch (e) {
      sa = null
    }
    if (sa == null) {
      ctx.error("Service account ${serviceAccountName} not found")
      return null
    }
    for (i = 0; i < sa.secrets.size(); i++) {
      if (sa.secrets[i].name.contains("dockercfg")) {
        secretName = sa.secrets[i].name
        break
      }
    }
    if (!secretName?.trim()) {
      error("Cannot find dockercfg secret for service account ${serviceAccountName}")
    }
  }
  return secretName
}
