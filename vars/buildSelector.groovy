#!/usr/bin/groovy

def call(Object ctx, String name) {
  def selector
  if (name.startsWith("build/")) {
      selector = ctx.openshift.selector(name) 
  } else {
      selector = ctx.openshift.selector("build", name)
  }
  return selector
}