#!/usr/bin/groovy

def call(Object ctx, String buildJSON) {
  ctx.sh "echo '${buildJSON}' | ${ctx.releaseCIPath()} run-build -f -"
}
