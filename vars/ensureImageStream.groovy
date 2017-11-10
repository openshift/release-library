#!/usr/bin/groovy

def call(Object ctx, String name) {
  echo "Ensuring image stream ${name} exists"
  ctx.openshift.withCluster() {
    ctx.openshift.apply([
      "kind": "ImageStream",
      "metadata": [
        "name": "${name}",
        "labels": [
          "created-by-ci": "true"
        ]
      ]
    ])
  }
}