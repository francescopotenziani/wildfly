/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.microprofile.health;

import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.dmr.ModelNode;

import java.io.IOException;

import static org.jboss.as.controller.operations.common.Util.getEmptyOperation;
import static org.wildfly.test.integration.microprofile.health.MicroProfileHealthUtils.testManagementOperation;

/**
 * @author <a href="http://xstefank.io/">Martin Stefanko</a> (c) 2021 Red Hat inc.
 */
public class MicroProfileHealthApplicationStartupOperationTestCase extends MicroProfileHealthApplicationStartupTestBase {

    void checkGlobalOutcome(ManagementClient managementClient, String operation, boolean mustBeUP, String probeName) throws IOException {
        final ModelNode address = new ModelNode();
        address.add("subsystem", "microprofile-health-smallrye");
        ModelNode checkOp = getEmptyOperation(operation, address);

        ModelNode response = managementClient.getControllerClient().execute(checkOp);
        testManagementOperation(response, mustBeUP, probeName);

    }
}
