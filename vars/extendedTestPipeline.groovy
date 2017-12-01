#!/usr/bin/groovy

def call(String jobName, String buildJobName, String deployJobName, String testImageTag, String workDir, String testCmd, String outputDir) {
  pipeline {
    agent any

    parameters {
      string(name: "BUILD_ID")
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
            this._buildParams = [
              [$class: "StringParameterValue", name: "BUILD_ID", value: params.BUILD_ID],
              [$class: "StringParameterValue", name: "REPO_OWNER", value: params.REPO_OWNER],
              [$class: "StringParameterValue", name: "REPO_NAME", value: params.REPO_NAME],
              [$class: "StringParameterValue", name: "PULL_REFS", value: params.PULL_REFS],
              [$class: "StringParameterValue", name: "PULL_BASE_REF", value: params.PULL_BASE_REF],
              [$class: "StringParameterValue", name: "PULL_BASE_SHA", value: params.PULL_BASE_SHA],
              [$class: "StringParameterValue", name: "PULL_NUMBER", value: params.PULL_NUMBER],
              [$class: "StringParameterValue", name: "PULL_PULL_SHA", value: params.PULL_PULL_SHA]
            ]
          }
        }
      }
      stage ("Ensure logging components exist") { steps {
          ensureLoggingComponents(this)
        }
      }
      stage ("Build") { steps {
          build job: buildJobName, parameters: this._buildParams, wait: true
        }
      }
      stage("Deploy") { steps {
          build job: deployJobName, parameters: this._buildParams, wait: true
        }
      }
      stage("Run Extended Test") {
        steps {
          script {
            podTemplate(
              cloud: "openshift",
              label: "extended_test",
              containers: [
                containerTemplate(
                  name: "extended_test", 
                  image: "${this._buildName}:${testImageTag}", 
                  ttyEnabled: true, 
                  command: "cat",
                  envVars: [
                    envVar(key: "KUBECONFIG", value: "/var/secrets/kubeconfig/admin.kubeconfig"),
                    envVar(key: "SKIP_CLEANUP", value: "true"),
                    envVar(key: "TEST_ONLY", value: "true"),
                    envVar(key: "JUNIT_REPORT", value: "true"),
                    envVar(key: "OPENSHIFT_SKIP_BUILD", value: "true")
                  ]
                )
              ],
              annotations: [
                podAnnotation(key: "alpha.image.policy.openshift.io/resolve-names", value: "*")
              ],
              volumes: [
                secretVolume (
                  secretName: "${this._buildName}",
                  mountPath: '/var/secrets/kubeconfig'
                )
              ]
            ) {
              node("extended_test") {
                container("origin-e2e") {
                  sh "cp /var/secrets/kubeconfig/admin.kubeconfig /tmp/admin.kubeconfig"
                  sh "cd ${workingDir} && ${testCmd} | tee /tmp/build_log.txt"
                  sh "mkdir -p artifacts && cp -R ${artifactDir}/* artifacts && cp /tmp/build_log.txt artifacts"
                  dir("artifacts") {
                    stash name: "artifacts"
                  }
                }
              }
            }
          }
        }
      }
      stage("Save artifacts") {
        steps {
          sh "mkdir -p artifacts"
          dir("artifacts") {
            unstash name: "artifacts"
          }
        }
      }
    }
    post {
      always {
        script {
          def workingDir = pwd()
          def artifactDir = "${workingDir}/artifacts"
          try {
            uploadArtifacts(this, artifactDir)
          } catch (e) {
            echo "error uploading artifacts: ${e}"
          }
          deleteWorkspace(this)
        }
      }
    }
  }
}
