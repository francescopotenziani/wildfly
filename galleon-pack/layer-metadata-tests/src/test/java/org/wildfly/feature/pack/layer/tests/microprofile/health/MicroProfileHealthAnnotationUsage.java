/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.feature.pack.layer.tests.microprofile.health;

import org.eclipse.microprofile.health.Readiness;

public class MicroProfileHealthAnnotationUsage {
    @Readiness
    String x;
}
