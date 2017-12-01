package com.redhat.openshift

import static com.redhat.openshift.BuildPipelineConfiguration.RPM_TAG
import static com.redhat.openshift.BuildUtilities.BuildLiteralDockerfile

/**
 * RPMImageInjectionStep ensures that RPMs are being served
 * from the cached RPM image stage, then tags the external
 * image into the build namespace and injects an RPM repo
 * pointing to the served RPMs into the image before tagging
 * it into the build ImageStream for downstream builds to
 * consume.
 */
class RPMImageInjectionStep implements Serializable, Step {
  ImageReference baseImage

  @Override
  List<String> Run(Object ctx, Object env, ReadOnlyInfoCache info) {
    String rpmRepo
    ctx.openshift.withCluster() {
      rpmRepo = ctx.openshift.selector("route", String.format("rpm-repo-%s", info.BuildName())).object().spec.host
      ctx.openshift.tag(this.baseImage.PullSpec(), String.format("%s:%s", this.baseImage.name, this.baseImage.tag))
    }
    String dockerfile = String.format("FROM %s:%s\n", this.baseImage.name, this.baseImage.tag) +
      "RUN echo \$'[built]\\n\\\n" +
      "name = Built RPMs\\n\\\n" +
      String.format("baseurl = http://%s\\n\\\n", rpmRepo) +
      "gpgcheck = 0\\n\\\n" +
      "enabled = 0\\n\\\n" +
      "\\n\\\n" +
      "[origin-local-release]\\n\\\n" +
      "name = Built RPMs\\n\\\n" +
      String.format("baseurl = http://%s\\n\\\n", rpmRepo) +
      "gpgcheck = 0\\n\\\n" +
      "enabled = 0' > /etc/yum.repos.d/built.repo\n"
    String buildName = BuildLiteralDockerfile(ctx, info, this.baseImage.name, dockerfile)
    if (buildName != "") {
      return Collections.singletonList(buildName)
    } else {
      return Collections.emptyList()
    }
  }

  @Override
  List<String> From() {
    return Arrays.asList(baseImage.PullSpec(), RPM_TAG)
  }

  @Override
  String To() {
    return baseImage.name
  }

  @Override
  String ID() {
    return String.format("RPMImageInjectionStep: from %s and %s, to %s", this.baseImage.PullSpec(), RPM_TAG, this.baseImage.name)
  }
}