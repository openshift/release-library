#!/usr/bin/groovy
import com.redhat.openshift.BuildPipelineConfiguration

import static com.redhat.openshift.BuildUtilities.EnsureImageStream
import static com.redhat.openshift.CleanupUtilities.CleanupArtifacts
import static com.redhat.openshift.CleanupUtilities.DeleteWorkspace
import static com.redhat.openshift.GCSUtilities.GenerateStartedMetadata
import static com.redhat.openshift.GCSUtilities.UploadArtifacts
import static com.redhat.openshift.OpenShiftUtilities.Exists
import static com.redhat.openshift.ReleaseUtilities.*
import static com.redhat.openshift.TestUtilities.EnsureLoggingComponents
import static com.redhat.openshift.TestUtilities.NewInfoCache

def call(BuildPipelineConfiguration buildPipelineConfiguration) {
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
      stage("Create ImageStream") {
        steps {
          script {
            EnsureImageStream(this, this._info)
          }
        }
      }
      stage("Run Configured Steps") {
        steps {
          script {
            buildPipelineConfiguration.Run(this, env, this._info)
          }
        }
      }
      stage("Create Image Namespace") {
        steps {
          script {
            EnsureReleaseNamespace(this, this._info)
          }
        }
      }
      stage("Tag Images") {
        steps {
          script {
            if (!Exists(this, "ConfigMap", this._info.BuildName)) {
              TagFullRelease(this, this._info, buildPipelineConfiguration.tagSpecification)
            }
          }
        }
      }
      stage("Create Build Result ConfigMap") {
        steps {
          script {
            if (!Exists(this, "ConfigMap", this._info.BuildName)) {
              CreateBuildConfigMap(this, this._info)
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
