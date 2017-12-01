package com.redhat.openshift

import static com.redhat.openshift.BuildUtilities.BuildLiteralDockerfile

/**
 * CacheStep caches build artifacts in an image.
 */
class CacheStep implements Serializable, Step {
  PipelineImageReference from
  PipelineImageReference to
  List<String> commands

  @Override
  List<String> Run(Object ctx, Object env, ReadOnlyInfoCache info) {
    List<String> commands = new ArrayList<>(Arrays.asList("umask 0002"))
    commands.addAll(this.commands)
    String formattedCommands = String.join(" && ", commands)
    String dockerfile = String.format("FROM %s:%s\nRUN %s", info.BuildName(), this.from.tag, formattedCommands)
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
    return String.format("CacheStep: from: %s, to: %s, running %s", this.from.PullSpec(), this.to.PullSpec(), String.join(" && ", this.commands))
  }
}