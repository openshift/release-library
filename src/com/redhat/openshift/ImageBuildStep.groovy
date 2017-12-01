package com.redhat.openshift

import static com.redhat.openshift.BuildPipelineConfiguration.SOURCE_TAG
import static com.redhat.openshift.BuildUtilities.BuildRepoDockerfile

/**
 * ImageBuildStep builds an image as part of the
 * project's release process.
 */
class ImageBuildStep implements Serializable, Step {
  PipelineImageReference from
  PipelineImageReference to
  String contextDir

  @Override
  List<String> Run(Object ctx, Object env, ReadOnlyInfoCache info) {
    String buildName = BuildRepoDockerfile(ctx, info, this.from.tag, this.to.tag, this.contextDir)
    if (buildName != "") {
      return Collections.singletonList(buildName)
    } else {
      return Collections.emptyList()
    }
  }

  @Override
  List<String> From() {
    return Arrays.asList(from.PullSpec(), SOURCE_TAG)
  }

  @Override
  String To() {
    return to.PullSpec()
  }

  @Override
  String ID() {
    return String.format("ImageBuildStep: from %s and %s, to %s, using %s", this.from.PullSpec(), SOURCE_TAG, this.to.PullSpec(), this.contextDir)
  }
}