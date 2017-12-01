package com.redhat.openshift

/**
 * TestStep runs a test.
 */
interface TestStep {
  void Run(Object ctx, Object env, ReadOnlyInfoCache info)
}