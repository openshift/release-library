#!/usr/bin/groovy
import com.redhat.openshift.TestStep

import static com.redhat.openshift.CleanupUtilities.CleanupArtifacts
import static com.redhat.openshift.CleanupUtilities.DeleteWorkspace
import static com.redhat.openshift.GCSUtilities.GenerateStartedMetadata
import static com.redhat.openshift.GCSUtilities.UploadArtifacts
import static com.redhat.openshift.TestUtilities.EnsureLoggingComponents
import static com.redhat.openshift.TestUtilities.NewInfoCache

def call(List<TestStep> testSteps) {
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
      stage("Configure Variables") {
        steps {
          script {
            this._info = NewInfoCache(this, env)
          }
        }
      }
      stage("Ensure logging components exist") {
        steps {
          script {
            EnsureLoggingComponents(this)
          }
        }
      }
      stage("Generate Started Metadata") {
        steps {
          script {
            GenerateStartedMetadata(this)
            UploadArtifacts(this, env)
          }
        }
      }
      stage("Run Configured Steps") {
        steps {
          script {
            for (TestStep step : testSteps) {
              step.Run(this, env, this._info)
            }
          }
        }
      }
    }
    post {
      always {
        CleanupArtifacts(this, env, this._info)
        DeleteWorkspace(this)
      }
    }
  }
}
