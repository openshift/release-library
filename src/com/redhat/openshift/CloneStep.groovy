package com.redhat.openshift

import static com.redhat.openshift.BuildUtilities.BuildLiteralDockerfile

/**
 * CloneStep caches the repository in an image.
 */
class CloneStep implements Serializable, Step {
  ImageReference from
  PipelineImageReference to = new PipelineImageReference(tag: BuildPipelineConfiguration.SOURCE_TAG)

  @Override
  List<String> Run(Object ctx, Object env, ReadOnlyInfoCache info) {
    String dockerfile = String.format("FROM %s\n", this.from.PullSpec()) +
      "ENV GIT_COMMITTER_NAME=developer GIT_COMMITTER_EMAIL=developer@redhat.com\n" +
      String.format("ENV REPO_OWNER=%s REPO_NAME=%s PULL_REFS=%s\n", env.REPO_OWNER, env.REPO_NAME, env.PULL_REFS) +
      "RUN umask 0002 && /usr/bin/release-ci cloneref --src-root=/go\n" +
      String.format("WORKDIR /go/src/github.com/%s/%s/\n", env.REPO_OWNER, env.REPO_NAME)
    String buildName = BuildLiteralDockerfile(ctx, info, this.to.tag, dockerfile)
    if (buildName != "") {
      return Collections.singletonList(buildName)
    } else {
      return Collections.emptyList()
    }
  }

  @Override
  List<String> From() {
    return Collections.singletonList(this.from.PullSpec())
  }

  @Override
  String To() {
    return this.to.PullSpec()
  }

  @Override
  String ID() {
    return String.format("CloneStep: from %s, to %s", this.from.PullSpec(), this.to.PullSpec())
  }
}