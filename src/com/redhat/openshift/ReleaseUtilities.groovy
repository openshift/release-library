package com.redhat.openshift

import static com.redhat.openshift.OpenShiftUtilities.*

class ReleaseUtilities implements Serializable {
  static void EnsureReleaseNamespace(Object ctx, ReadOnlyInfoCache info) {
    String projectName = String.format("images-%s", info.BuildName())
    ctx.echo "EnsureReleaseNamespace: setting up Project ${projectName}"
    ctx.openshift.withCluster() {
      if (!Exists(ctx, "Project", projectName)) {
        ctx.echo "EnsureReleaseNamespace: creating Project ${projectName}"
        ctx.openshift.newProject(projectName)
      }
      ctx.openshift.withProject(projectName) {
        ctx.echo "EnsureReleaseNamespace: adding admin Role to Group system:serviceaccounts:${info.JenkinsNamespace()}"
        ctx.openshift.raw("policy", "add-role-to-group", "admin", "system:serviceaccounts:${info.JenkinsNamespace()}")
        ctx.openshift.raw("policy", "add-role-to-user", "view", "system:anonymous")
        ctx.openshift.raw("policy", "add-role-to-user", "system:image-puller", "system:anonymous")
      }
    }
    ctx.echo "EnsureReleaseNamespace: set up Project ${projectName}"
  }

  static void TagFullRelease(Object ctx, ReadOnlyInfoCache info, TagSpecification tagSpecification) {
    String projectName = String.format("images-%s", info.BuildName())
    ctx.echo "TagFullRelease: tagging a full set of release images into ${projectName}"
    ctx.openshift.withCluster() {
      ctx.echo "TagFullRelease: tagging images from stable Project ${tagSpecification.namespace}"
      ctx.openshift.withProject(tagSpecification.namespace) {
        ctx.openshift.selector("is").withEach {
          def name = it.object().metadata.name
          ctx.echo "TagFullRelease: tagging images from ImageStream ${name}"

          def overrideTag = tagSpecification.tagOverrides[name]
          if (overrideTag) {
            ctx.openshift.withProject(projectName) {
              ctx.echo "TagFullRelease: overriding tag from ${tagSpecification.namespace}/${name}:${overrideTag} to ${name}:ci"
              ctx.openshift.tag("${tagSpecification.namespace}/${name}:${overrideTag}", "${name}:ci")
            }
          } else {
            def tags = it.object().status.tags
            for (tag in tags) {
              // Only tag an image stream if it contains the tag we're looking for
              if (tag.tag == tagSpecification.tag) {
                ctx.openshift.withProject(projectName) {
                  ctx.echo "TagFullRelease: cross-tagging from ${tagSpecification.namespace}/${name}:${tagSpecification.tag} to ${name}:ci"
                  ctx.openshift.tag("${tagSpecification.namespace}/${name}:${tagSpecification.tag}", "${name}:ci")
                }
              }
            }
          }
        }
      }
      ctx.openshift.withProject(info.JenkinsNamespace()) {
        ctx.echo "TagFullRelease: tagging images from the latest build in ${info.JenkinsNamespace()}"
        def tags = ctx.openshift.selector("is", info.BuildName()).object().status.tags
        for (tag in tags) {
          ctx.openshift.withProject(projectName) {
            ctx.echo "TagFullRelease: cross-tagging from ${info.JenkinsNamespace()}/${info.BuildName()}:${tag.tag} to ${tag.tag}:ci"
            ctx.openshift.tag("${info.JenkinsNamespace()}/${info.BuildName()}:${tag.tag}", "${tag.tag}:ci")
          }
        }
      }
    }
    ctx.echo "TagFullRelease: tagged a full set of release images into ${projectName}"
  }

  static void CreateBuildConfigMap(Object ctx, ReadOnlyInfoCache info) {
    String projectName = String.format("images-%s", info.BuildName())
    ctx.echo "CreateBuildConfigMap: creating ConfigMap ${info.BuildName()} with build metadata"
    ctx.openshift.withCluster() {
      String imageBase
      ctx.openshift.withProject(projectName) {
        def originImageStream = ctx.openshift.selector("imagestream", "origin").object()
        if (originImageStream.status.publicDockerImageRepository) {
          imageBase = originImageStream.status.publicDockerImageRepository
        } else {
          imageBase = originImageStream.status.dockerImageRepository
        }
      }
      ctx.echo "CreateBuildConfigMap: recording image base as ${imageBase}"
      ctx.echo "CreateBuildConfigMap: recording image Namespace as ${projectName}"
      if (Exists(ctx, "Route", "rpm-repo-${info.BuildName()}")) {
        def routeHost = ctx.openshift.selector("route/rpm-repo-${info.BuildName()}").object().spec.host
        ctx.echo "CreateBuildConfigMap: recording RPM repo as http://${routeHost}"
        ctx.openshift.raw("create", "configmap", info.BuildName(),
          "--from-literal", "image-base=${imageBase}",
          "--from-literal", "namespace=${projectName}",
          "--from-literal", "rpm-repo=http://${routeHost}"
        )
      } else {
        ctx.openshift.raw("create", "configmap", info.BuildName(),
          "--from-literal", "image-base=${imageBase}",
          "--from-literal", "namespace=${projectName}"
        )
      }
      ctx.openshift.raw("label",
        "configmap", info.BuildName(),
        "${BUILD_LABEL}=${info.BuildName()}",
        "${JOB_ID_LABEL}=${info.JobID()}",
        "${CREATED_BY_CI_LABEL}=${CREATED_BY_CI_VALUE}",
        "${PERSISTS_LABEL}=${PERSISTS_VALUE}"
      )
    }
    ctx.echo "CreateBuildConfigMap: created ConfigMap ${info.BuildName()} with build metadata"
  }
}