#!/usr/bin/groovy

def call(String jobName, String stableNamespace, String stableImageTag, Map<String,String> tagOverrides) {
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
      stage("Extract Config Vars") {
        steps {
          script {
            openshift.withCluster() {
              def cm = openshift.selector("cm/${this._buildName}").object()
              this._imageBase = cm.data["image-base"]
              this._rpmRepo = cm.data["rpm-repo"]
              this._kubeconfig = cm.data["kubeconfig"]
            }
          }
        }
      }
      stage("Create Image Namespace") {
        when { expression { return projectExists(this, "images-${this._buildName}") } }
        steps {
          script {
            def currentProject = "${env.PROJECT_NAME}"
            def setPermissions = {
              openshift.withProject("images-${this._buildName}") {
                openshift.raw("policy", "add-role-to-group", "admin", "system:serviceaccounts:${currentProject}")
                openshift.raw("policy", "add-role-to-user", "view", "system:anonymous")
                openshift.raw("policy", "add-role-to-user", "system:image-puller", "system:anonymous")
              }
            }
            openshift.withCluster() {
              if ("${params.PROVISIONER_TOKEN}".length() > 0) {
                openshift.doAs("${params.PROVISIONER_TOKEN}") {
                  openshift.newProject("images-${this._buildName}")
                  setPermissions()
                }
              } else {
                openshift.newProject("images-${this._buildName}")
                setPermissions()
              }
            }
          }
        }
      }
      stage("Tag Images") {
        when { expression { return this._imageBase ? false : true } }
        steps {
          script {
            def currentProject = "${env.PROJECT_NAME}"
            openshift.withCluster() {
              openshift.withProject("${stableImageNamespace}") {
                openshift.selector("is").withEach {
                  def name = it.object().metadata.name
                
                  def overrideTag = tagOverrides[name]
                  if (overrideTag) {
                    openshift.withProject("images-${this._buildName}") {
                      openshift.tag("${stableImageNamespace}//${name}:${overrideTag}", "${name}:ci")
                    }
                  }
                  else {
                    def tags = openshift.selector("is", "${this._buildName}").object().status.tags
                    for (i = 0; i < tags.size(); i++) {
                      // Only tag an image stream if it contains the stableImageTag we're looking for
                      if (tags[i].tag == "${stableImageTag}") {
                        openshift.withProject("images-${this._buildName}") {
                          openshift.tag("${stableImageNamespace}/${name}:${stableImageTag}", "${name}:ci")
                        }
                      }
                    }
                  }
                }
              }
              // Apply tags resulting from current build
              def tags = openshift.selector("is", "${this._buildName}").object().status.tags
              for (i = 0; i < tags.size(); i++) {
                def tag = tags[i].tag
                openshift.withProject("images-${this._buildName}") {
                  openshift.tag("${currentProject}/${this._buildName}:${tag}", "${tag}:ci")
                }
              }

              // Get the image-base for origin install
              openshift.withProject("images-${this._buildName}") {
                def originImageStream = openshift.selector("is", "origin").object()
                if (originImageStream.status.publicDockerImageRepository) {
                  this._imageBase = originImageStream.status.publicDockerImageRepository
                } else {
                  this._imageBase = originImageStream.status.dockerImageRepository
                }
              }
              sh "oc patch cm/${this._buildName} --patch '{ \"data\": { \"image-base\": \"${this._imageBase}\" } }'"
            }
          }
        }
      }
      stage("Stash data directory") {
        when { expression { return this._kubeconfig ? false : true } }
        steps {
          script {
            dir ("cluster/test-deploy/data") {
              stash "data-files"
            }
          }
        }
      }
      stage("Run Deployment") {
        when { expression { return this._kubeconfig ? false : true } }
        steps {
          script {
            // TODO: Grab origin-gce from a stable image stream as well
            podTemplate(
              cloud: "openshift",
              label: "origin-gce",
              containers: [
                containerTemplate(name: "origin-gce", image: "openshift/origin-gce:latest", ttyEnabled: true, command: "cat")
              ],
              volumes: [
                secretVolume (
                  secretName: 'gce-provisioner',
                  mountPath: '/var/secrets/gce-provisioner'
                ),
                secretVolume (
                  secretName: 'gce-ssh',
                  mountPath: '/var/secrets/gce-ssh'
                )
              ]
            ) {
              node("origin-gce") {
                container("origin-gce") {
                  sh "mkdir data-files"
                  dir("data-files") {
                    unstash "data-files"
                    sh "cp * /usr/share/ansible/openshift-ansible-gce/playbooks/files"
                  }
                  sh "cp -L /var/secrets/gce-provisioner/*.json /var/secrets/gce-ssh/ssh* /usr/share/ansible/openshift-ansible-gce/playbooks/files"
                  def instancePrefix = "${this._buildName}".take(25)
                  def script = $/cd $${WORK} && HOME=/home/cloud-user \
  $${WORK}/entrypoint.sh env INSTANCE_PREFIX=${instancePrefix} ansible-playbook -vvv \
  -e 'oreg_url=${this._imageBase}-$${component}:ci' \
  -e "openshift_test_repo=${this._rpmRepo}" playbooks/launch.yaml/$
                  sh script
                  sh "mkdir kubeconfig"
                  dir("kubeconfig") {
                    sh "cp /tmp/admin.kubeconfig ."
                    stash "kubeconfig"
                  }
                }
              }
            }
          }
        }
      }
      stage("Save Kubeconfig") {
        when { expression { return this._kubeconfig ? false : true } }
        steps {
          script {
            sh "mkdir -p kubeconfig"
            dir("kubeconfig") {
              unstash "kubeconfig"
              openshift.withCluster() {
                openshift.selector("secret", "${this._buildName}").delete("--ignore-not-found")
                openshift.raw("create", "secret", "generic", "${this._buildName}", "--from-file", "admin.kubeconfig=./admin.kubeconfig")
                sh "oc patch cm/${this._buildName} --patch '{ \"data\": { \"kubeconfig\": \"${this._buildName}\" } }'"
              }
            }
          }
        }
      }
    }
  }
}

