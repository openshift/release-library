#!/usr/bin/env groovy

def call(String jobName, String buildJobName, String baseTag, String args, String memoryLimit, String cpuRequest) {
  pipeline {
    agent any

    parameters {
      string(name: "BUILD_ID")
      string(name: "JOB_SPEC")
      string(name: "REPO_OWNER")
      string(name: "REPO_NAME")
      string(name: "PULL_REFS")
      string(name: "PULL_BASE_REF")
      string(name: "PULL_BASE_SHA")
      string(name: "PULL_NUMBER")
      string(name: "PULL_PULL_SHA")
    }

    stages {
      stage ("Configure Variables") {
        steps {
          script {
            this._buildName = buildName(this)
          }
        }
      }
      stage ("Launch Build") {
        steps {
          script {
            def buildParams = []
            for (def param in params) {
              buildParams.add([$class: "StringParameterValue", name: param.key, value: param.value])
            }
            build job: buildJobName, parameters: buildParams, wait: true
          }
        }
      }
      stage("Wait For Image") {
        steps {
          waitForTag(this, this._buildName, baseTag, 7200)
        }
      }
      stage("Run Test") {
        steps {
          ensureLoggingComponents(this)
          script {
            def toolsImageRef = imageStreamTagRef(this, "release-ci", "binary")
            def baseImageRef = imageStreamTagRef(this, this._buildName, baseTag)
            def pullSecretRef = dockerCfgSecret(this, "default")
            def runId = "${this._buildName}-${jobName}-${currentBuild.number}"
            this._runId = runId

            def template = libraryResource "org/origin/tests.yaml"
            writeFile file: "tests.yaml", text: template
            createTemplate(this, "tests.yaml",
              "JOB_NICKNAME=${jobName}",
              "JOB_ARGS=${args}",
              "MEMORY_LIMIT=${memoryLimit}",
              "MEMORY_REQUEST=${memoryLimit}",
              "CPU_REQUEST=${cpuRequest}",
              "BUILD_NAME=${_buildName}",
              "RUN_ID=${runId}",
              "BASE_IMAGE_REF=${baseImageRef}",
              "TOOLS_IMAGE_REF=${toolsImageRef}",
              "PULL_SECRET_NAME=${pullSecretRef}",
              "JOB_NAME=${env.JOB_NAME}",
              "BUILD_NUMBER=${env.BUILD_NUMBER}",
              "REPO_OWNER=${params.REPO_OWNER}",
              "REPO_NAME=${params.REPO_NAME}",
              "PULL_BASE_REF=${params.PULL_BASE_REF}",
              "PULL_BASE_SHA=${params.PULL_BASE_SHA}",
              "PULL_REFS=${params.PULL_REFS}",
              "PULL_NUMBER=${params.PULL_NUMBER}",
              "PULL_PULL_SHA=${params.PULL_PULL_SHA}"
            )

            waitForPods(this, ["run": "${runId}"], 7200)
          }
        }
      }
    }
    post {
      always {
        cleanupArtifacts(this, ["run":"${this._runId}"])
        deleteWorkspace(this)
      }
    }
  }
}