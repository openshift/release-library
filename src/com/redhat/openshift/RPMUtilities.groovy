package com.redhat.openshift

import groovy.json.JsonSlurperClassic

import static com.redhat.openshift.BuildPipelineConfiguration.RPM_SERVE_LOCATION
import static com.redhat.openshift.BuildPipelineConfiguration.RPM_TAG
import static com.redhat.openshift.OpenShiftUtilities.*

class RPMUtilities implements Serializable {
  static void EnsureRPMServer(Object ctx, ReadOnlyInfoCache info) {
    String name = String.format("rpm-repo-%s", info.BuildName())
    if (Exists(ctx, "Route", name)) {
      ctx.echo "EnsureRPMServer: not starting new RPM server, Route ${name} already exists"
      return
    }
    String rpmImage
    ctx.openshift.withCluster() {
      String imageStreamRepo = ctx.openshift.selector("imagestream", info.BuildName()).object().status.dockerImageRepository
      rpmImage = String.format("%s:%s", imageStreamRepo, RPM_TAG)
      ctx.echo "EnsureRPMServer: serving RPMs from ${rpmImage}"
      String output = ctx.openshift.raw("run", name,
        "--labels", "${BUILD_LABEL}=${info.BuildName()},app=rpm-repo-${info.BuildName()},${JOB_ID_LABEL}=${info.JobID()},${CREATED_BY_CI_LABEL}=${CREATED_BY_CI_VALUE},${PERSISTS_LABEL}=${PERSISTS_VALUE}",
        "--image", "${rpmImage}",
        "--image-pull-policy", "Always",
        "--expose", "--port", "8080",
        "--dry-run", "--output", "json",
        "--", "/bin/bash", "-c", "'cd ${RPM_SERVE_LOCATION} && python -m SimpleHTTPServer 8080'"
      ).out
      // We get concatenated JSON documents but need a list
      // or the Groovy parser just gives us the first object
      // so we use this hack to get a list
      output = "[${output}]"
      output = output.replace("}\n{", "},\n{")
      def list = new JsonSlurperClassic().parseText(output)

      def genericProbe = [
        "httpGet"            : [
          "path"  : "/",
          "port"  : 8080,
          "scheme": "HTTP"
        ],
        "initialDelaySeconds": 1,
        "timeoutSeconds"     : 1,
        "periodSeconds"      : 10,
        "successThreshold"   : 1,
        "failureThreshold"   : 3
      ]
      for (item in list) {
        if (item.kind == "DeploymentConfig") {
          item.spec.template.spec.containers[0]["livenessProbe"] = genericProbe
          item.spec.template.spec.containers[0]["readinessProbe"] = genericProbe
          break
        }
      }
      ctx.openshift.apply(list).narrow("service").expose()
      WaitForDeployment(ctx, name)
    }
    ctx.echo "EnsureRPMServer: served RPMs from ${rpmImage}"
  }
}