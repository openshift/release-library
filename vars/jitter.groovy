#!/usr/bin/env groovy

// sleep for a random number of seconds
def call(Object ctx, int min, int max) {
  def randomSecs = ctx.sh(script: "awk -v min=${min} -v max=${max} 'BEGIN{srand(); print int(min+rand()*(max-min+1))}'", returnStdout: true)
  def intSecs = randomSecs as Integer
  ctx.echo "jitter: sleeping for ${intSecs} seconds"
  ctx.sleep intSecs
}
