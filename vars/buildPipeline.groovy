#!/usr/bin/groovy

def call(String jobName, CloneStep cloneStep, java.util.ArrayList<BuildStep> buildSteps) {
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
            this._jobId = "${jobName}-${this._buildName}-${env.BUILD_NUMBER}"
          }
        }
      }
      stage ("Ensure logging components exist") { steps {
          ensureLoggingComponents(this)
        }
      }
      stage ("Create ImageStream") { steps {
          ensureImageStream(this, "${this._buildName}")
        }
      }
      stage("Clone Source") {
        steps {
          if (!imageStreamTagExists(this, "${this._buildName}", cloneStep.ToTag())) {
            cloneStep.LaunchBuild(this, this._buildName, this._jobId, params.REPO_OWNER, params.REPO_NAME, params.PULL_REFS)
          }
        }
      }
      stage("Build Images") {
        steps {
          script {
            for(step in buildSteps) {
              if (!imageStreamTagExists(this, "${this._buildName}", step.ToTag())) {
                step.LaunchBuild(this, this._buildName, this._jobId)
              }
            }
          }
        }
      }
    }
    post {
      always {
        cleanupArtifacts(this, ["job-id": "${this._jobId}"])
        deleteWorkspace(this)
      }
    }
  }
}
