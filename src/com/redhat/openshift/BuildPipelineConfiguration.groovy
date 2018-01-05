package com.redhat.openshift

import static com.redhat.openshift.BuildUtilities.WaitForBuilds
import static com.redhat.openshift.OpenShiftUtilities.Exists
import static com.redhat.openshift.RPMUtilities.EnsureRPMServer

class BuildPipelineConfiguration implements Serializable {
  /**
   * The following minimal fields allow the user to buy into
   * our normal conventions without worrying about how the
   * pipeline flows. Use these preferentially for new projects
   * with simple/conventional build configurations.
   */

  /**
   * testBaseImage is the image we base our pipeline image
   * caches on. It should contain all build-time dependencies
   * for the project.
   */
  ImageReference testBaseImage

  /**
   * testBaseTag is the tag on the "normal" stable Image-
   * Stream used for the test base. This allows users to
   * be more succinct in their image base declaration.
   */
  String testBaseTag

  /**
   * The following commands describe how binaries, test
   * binaries and RPMs are built baseImage source in the repo
   * under test. If a list is omitted by the user, the
   * resulting image is not built.
   */
  List<String> binaryBuildCommands
  List<String> testBinaryBuildCommands
  List<String> rpmBuildCommands

  /**
   * rpmBuildLocation is where RPms are deposited after
   * being built. If unset, this will default under the
   * repository root to _output/local/releases/rpms/.
   */
  String rpmBuildLocation

  /**
   * The following lists of base images describe which
   * images are going to be necessary baseImage outside of
   * the pipeline. RPM repositories will be injected
   * into the baseRPMImages for downstream image builds
   * that require built project RPMs.
   */
  List<ImageReference> baseImages = new ArrayList<>()
  List<ImageReference> baseRPMImages = new ArrayList<>()

  /**
   * images describes the images that are built baseImage the
   * project as part of the release process
   */
  List<ImageBuildSpecification> images = new ArrayList<>()

  /**
   * The following options determine how the full release
   * is assembled.
   */
  TagSpecification tagSpecification = new TagSpecification(
    namespace: "stable",
    tag: "latest",
    tagOverrides: new HashMap<String, String>()
  )

  /**
   * rawSteps are literal Steps that should be included
   * in the final pipeline.
   */
  List<Step> rawSteps = new ArrayList<>()

  /**
   * steps describes the steps we will run to create a
   * full release of the project
   */
  private List<Graph> steps = new ArrayList<>()
  private Graph rpmBuildStep

  /**
   * Run uses a breadth-first trajectory to run all steps,
   * doing as much work in parallel as possible. We will
   * first run the build cache steps, then serve RPMs if
   * appropriate and finally build project images.
   */
  void Run(Object ctx, Object env, ReadOnlyInfoCache info) {
    ctx.echo "Run: running build steps"
    this.resolveSteps(ctx, env)

    // We want to build as many steps in parallel as possible,
    // so we launch as many as possible and leave scheduling
    // and dependency resolution to OpenShift. The one place
    // where this is not possible is RPMs -- we need to start
    // a server deployment once we've built RPMs and launch
    // builds that expect to `yum install` after. That's not
    // a dependency we can describe in a Build object, so we
    // must partition our build tree into three sections:
    //   1. steps that need to happen to build RPMs
    //   2. steps that need to happen after we serve RPMs
    //   3. unrelated steps
    // We will run steps in groups 1 and 3 first, wait for
    // all steps in group 1 to finish, serve RPMs, trigger
    // all steps in group 2 and wait for all outstanding
    // builds.
    ctx.echo "Run: partitioning steps into sub-trees"
    List<Graph> rpmBuildSteps = new ArrayList<>()
    rpmBuildSteps.add(this.rpmBuildStep)
    List<Graph> postRPMBuildSteps = new ArrayList<>()
    List<Graph> unrelatedSteps = new ArrayList<>()

    // we need to fully qualify this import or hudson silently
    // aliases `Queue' and we get something that doesn't compile
    java.util.Queue<Graph> toVisit = new LinkedList<>()
    List<Graph> visited = new ArrayList<>()
    toVisit.addAll(this.steps)
    while (!toVisit.isEmpty()) {
      Graph step = toVisit.pop()
      if (visited.contains(step)) {
        continue
      } else {
        visited.add(step)
      }
      if (isAncestorOf(step, this.rpmBuildStep)) {
        rpmBuildSteps.add(step)
      } else if (isDescendantOf(step, this.rpmBuildStep)) {
        postRPMBuildSteps.add(step)
      } else {
        unrelatedSteps.add(step)
      }
      toVisit.addAll(step.children)
    }

    List<String> triggeredUnrelatedBuilds = new ArrayList<>()
    for (Graph step : unrelatedSteps) {
      ctx.echo "Run: launching unrelated step ${step.toString()}"
      triggeredUnrelatedBuilds.addAll(step.data.Run(ctx, env, info))
    }

    if (!rpmBuildSteps.isEmpty()) {
      List<String> triggeredRPMBuilds = new ArrayList<>()
      for (Graph step : rpmBuildSteps) {
        ctx.echo "Run: launching RPM step ${step.toString()}"
        triggeredRPMBuilds.addAll(step.data.Run(ctx, env, info))
      }
      if (!triggeredRPMBuilds.isEmpty()) {
        ctx.echo "Run: waiting for RPM steps"
        WaitForBuilds(ctx, triggeredRPMBuilds)
      }
      ctx.echo "Run: serving RPMs"
      EnsureRPMServer(ctx, info)
    }

    List<String> triggeredBuilds = new ArrayList<>()
    for (Graph step : postRPMBuildSteps) {
      ctx.echo "Run: launching post-RPM step ${step.toString()}"
      triggeredBuilds.addAll(step.data.Run(ctx, env, info))
    }

    // we need to wait for all of our builds to finish now, but
    // it is possible that a different instance of this pipeline
    // started a build before us and we latched on to it, but the
    // other instance finished already and the build got deleted,
    // so we should filter out builds that no longer exist. This
    // is still racy since the build could get deleted before we
    // start the watch but after we filter, but that's a lot less
    // likely than it being deleted between when we latch on and
    // when we wait for it (as RPM builds will have happened in
    // between)
    List<String> builds = new ArrayList<>()
    for (String build : triggeredUnrelatedBuilds) {
      if (Exists(ctx, "build", build)) {
        builds.add(build)
      } else {
        ctx.echo "Run: build ${build} was launched but no longer exists"
      }
    }
    for (String build : triggeredBuilds) {
      if (Exists(ctx, "build", build)) {
        builds.add(build)
      } else {
        ctx.echo "Run: build ${build} was launched but no longer exists"
      }
    }
    if (!builds.isEmpty()) {
      ctx.echo "Run: waiting for all remaining steps"
      WaitForBuilds(ctx, builds)
    }
    ctx.echo "Run: ran build steps"
  }

  private void resolveSteps(Object ctx, Object env) {
    ctx.echo "resolveSteps: resolving steps from user input"
    List<Step> steps = new ArrayList<>()
    steps.addAll(this.rawSteps)
    steps.add(this.resolveCloneStep(env))

    String rpmBase = SOURCE_TAG
    if (this.binaryBuildCommands != null) {
      steps.add(this.resolveBinaryBuildStep())
      rpmBase = BINARIES_TAG
    }

    if (this.testBinaryBuildCommands != null) {
      steps.add(this.resolveTestBinaryBuildStep())
    }

    if (this.rpmBuildCommands != null) {
      steps.add(this.resolveRPMBuildStep(rpmBase))
    }
    steps.addAll(this.resolveBaseImageTagSteps())
    steps.addAll(this.resolveRPMImageInjectionSteps())
    steps.addAll(this.resolveImageBuildSteps())

    List<Graph> stepNodes = new ArrayList<>()
    for (Step step : steps) {
      Graph graph = new Graph(
        data: step,
        parents: new ArrayList<Graph>(),
        children: new ArrayList<Graph>()
      )
      stepNodes.add(graph)
      if (graph.data.To() == RPM_TAG) {
        this.rpmBuildStep = graph
      }
    }

    List<Graph> roots = new ArrayList<>()
    for (Graph step : stepNodes) {
      boolean isRoot = true
      for (Graph other : stepNodes) {
        if (step.data.From().contains(other.data.To())) {
          isRoot = false
        }
      }
      if (isRoot) {
        roots.add(step)
      }
    }

    for (Graph node : stepNodes) {
      for (Graph other : stepNodes) {
        if (other.data.From().contains(node.data.To())) {
          other.parents.add(node)
          node.children.add(other)
        }
      }
    }
    this.steps = roots
    for (Graph step : stepNodes) {
      StringBuilder info = new StringBuilder()
      info.append("resolveSteps: have step: ${step.toString()}")
      for (Graph parent : step.parents) {
        info.append("\n\t parent: ${parent.toString()}")
      }
      for (Graph child : step.children) {
        info.append("\n\t child: ${child.toString()}")
      }
      ctx.echo info.toString()
    }
    ctx.echo "resolveSteps: resolved steps from user input"
  }

  private class Graph implements Serializable {
    public List<Graph> parents
    public Step data
    public List<Graph> children

    @Override
    String toString() {
      return this.@data.ID()
    }
  }

  private boolean isAncestorOf(Graph ancestor, Graph other) {
    // we need to fully qualify this import or hudson silently
    // aliases `Queue' and we get something that doesn't compile
    java.util.Queue<Graph> toVisit = new LinkedList<>()
    List<Graph> visited = new ArrayList<>()
    toVisit.add(ancestor)
    while (!toVisit.isEmpty()) {
      Graph step = toVisit.pop()
      if (visited.contains(step)) {
        continue
      } else {
        visited.add(step)
      }
      if (step.children.contains(other)) {
        return true
      }
      toVisit.addAll(step.children)
    }

    return false
  }

  private boolean isDescendantOf(Graph descendant, Graph other) {
    // we need to fully qualify this import or hudson silently
    // aliases `Queue' and we get something that doesn't compile
    java.util.Queue<Graph> toVisit = new LinkedList<>()
    List<Graph> visited = new ArrayList<>()
    toVisit.add(descendant)
    while (!toVisit.isEmpty()) {
      Graph step = toVisit.pop()
      if (visited.contains(step)) {
        continue
      } else {
        visited.add(step)
      }
      if (step.parents.contains(other)) {
        return true
      }
      toVisit.addAll(step.parents)
    }

    return false
  }

  static final String SOURCE_TAG = "src"

  Step resolveCloneStep(Object env) {
    if (this.testBaseImage != null && this.testBaseTag != null) {
      throw new IllegalArgumentException("An explicit test base image and test base tag cannot be provided at once.")
    } else if (this.testBaseImage == null && this.testBaseTag == null) {
      return new CloneStep(from: new ImageReference(
        namespace: "stable",
        name: String.format("%s-test-base", env.REPO_NAME),
        tag: "latest"
      ))
    } else if (this.testBaseImage == null && this.testBaseTag != null) {
      return new CloneStep(from: new ImageReference(
        namespace: "stable",
        name: String.format("%s-test-base", env.REPO_NAME),
        tag: this.testBaseTag
      ))
    } else if (this.testBaseImage != null && this.testBaseTag == null) {
      return new CloneStep(from: this.testBaseImage)
    }
  }

  static final String BINARIES_TAG = "bin"
  static final String TEST_BINARIES_TAG = "test-bin"
  static final String RPM_TAG = "rpm"
  static final String DEFAULT_RPM_LOCATION = "_output/local/releases/rpms/"
  static final String RPM_SERVE_LOCATION = "/srv/repo"

  Step resolveBinaryBuildStep() {
    return new CacheStep(
      from: new PipelineImageReference(tag: SOURCE_TAG),
      to: new PipelineImageReference(tag: BINARIES_TAG),
      commands: this.binaryBuildCommands
    )
  }

  Step resolveTestBinaryBuildStep() {
    return new CacheStep(
      from: new PipelineImageReference(tag: SOURCE_TAG),
      to: new PipelineImageReference(tag: TEST_BINARIES_TAG),
      commands: this.testBinaryBuildCommands
    )
  }

  Step resolveRPMBuildStep(String baseTag) {
    List<String> buildCommands = this.rpmBuildCommands
    String rpmBuildLocation
    if (this.rpmBuildLocation != null) {
      rpmBuildLocation = this.rpmBuildLocation
    } else {
      rpmBuildLocation = DEFAULT_RPM_LOCATION
    }
    buildCommands.add(String.format("ln -s \\\$( pwd )/%s %s", rpmBuildLocation, RPM_SERVE_LOCATION))

    return new CacheStep(
      from: new PipelineImageReference(tag: baseTag),
      to: new PipelineImageReference(tag: RPM_TAG),
      commands: buildCommands
    )
  }

  List<Step> resolveBaseImageTagSteps() {
    List<Step> baseImageTagSteps = new ArrayList<>()
    for (ImageReference baseImage : this.baseImages) {
      baseImageTagSteps.add(new BaseImageTagStep(baseImage: baseImage))
    }
    return baseImageTagSteps
  }

  List<Step> resolveRPMImageInjectionSteps() {
    List<Step> baseImageTagSteps = new ArrayList<>()
    for (ImageReference baseImage : this.baseRPMImages) {
      baseImageTagSteps.add(new RPMImageInjectionStep(baseImage: baseImage))
    }
    return baseImageTagSteps
  }

  List<Step> resolveImageBuildSteps() {
    List<Step> imageBuildSteps = new ArrayList<>()
    for (ImageBuildSpecification specification : this.images) {
      imageBuildSteps.add(new ImageBuildStep(
        from: new PipelineImageReference(tag: specification.from),
        to: new PipelineImageReference(tag: specification.to),
        contextDir: specification.contextDir
      ))
    }
    return imageBuildSteps
  }
}