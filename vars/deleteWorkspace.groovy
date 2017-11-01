#!/usr/bin/groovy

def call(Object ctx) {
  try {
    ctx.deleteDir()
  } catch (e) {
    ctx.echo "Error deleting workspace: ${e}"
  }
}
