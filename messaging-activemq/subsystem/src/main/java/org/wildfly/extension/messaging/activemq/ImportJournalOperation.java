/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.messaging.activemq;

import static org.jboss.as.controller.PathAddress.EMPTY_ADDRESS;
import static org.jboss.as.controller.RunningMode.NORMAL;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.cli.commands.tools.xml.XmlDataImporter;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.extension.messaging.activemq.logging.MessagingLogger;


/**
 * Import a dump of Artemis journal in a running Artemis server.
 * WildFly must be running in NORMAL mode to perform this operation.
 *
 * The dump file MUST be on WildFly host. It is not attached to the operation stream.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2015 Red Hat inc.
 */
public class ImportJournalOperation extends AbstractArtemisActionHandler {

    private static AttributeDefinition FILE = SimpleAttributeDefinitionBuilder.create("file", PathResourceDefinition.PATH)
            .setAllowExpression(false)
            .setRequired(true)
            .build();

    private static AttributeDefinition LEGACY_PREFIXES = SimpleAttributeDefinitionBuilder.create("legacy-prefixes", ModelType.BOOLEAN)
            // import with legacy prefix by default for backwards compatibility
            .setDefaultValue(ModelNode.TRUE)
            .setAllowExpression(false)
            .setRequired(false)
            .build();

    private static final String OPERATION_NAME = "import-journal";

    static final ImportJournalOperation INSTANCE = new ImportJournalOperation();

    private ImportJournalOperation() {

    }

    static void registerOperation(final ManagementResourceRegistration registry, final ResourceDescriptionResolver resourceDescriptionResolver) {
        registry.registerOperationHandler(new SimpleOperationDefinitionBuilder(OPERATION_NAME, resourceDescriptionResolver)
                        .addParameter(FILE)
                        .addParameter(LEGACY_PREFIXES)
                        .setRuntimeOnly()
                        .setReplyValueType(ModelType.BOOLEAN)
                        .build(),
                INSTANCE);
    }

    @Override
    protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
        if (context.getRunningMode() != NORMAL) {
            throw MessagingLogger.ROOT_LOGGER.managementOperationAllowedOnlyInRunningMode(OPERATION_NAME, NORMAL);
        }
        checkAllowedOnJournal(context, OPERATION_NAME);

        String file = FILE.resolveModelAttribute(context, operation).asString();
        boolean legacyPrefixes = LEGACY_PREFIXES.resolveModelAttribute(context, operation).asBoolean();

        final XmlDataImporter importer = new XmlDataImporter();
        importer.legacyPrefixes = legacyPrefixes;

        TransportConfiguration transportConfiguration = createInVMTransportConfiguration(context);
        try (
                InputStream is = new FileInputStream(new File(file));
                ServerLocator serverLocator = ActiveMQClient.createServerLocator(false, transportConfiguration);
                ClientSessionFactory sf = serverLocator.createSessionFactory()
        ) {
            ClientSession session = sf.createSession();
            importer.process(is, session);
        } catch (Exception e) {
            throw new OperationFailedException(e);
        }
    }

    /**
     * The XmlDataImporter requires a connector to connect to the artemis broker.
     *
     * We require to use a in-vm one so that importing a journal is not subject to any network connection problem.
     */
    private TransportConfiguration createInVMTransportConfiguration(OperationContext context) throws OperationFailedException {
        final Resource serverResource = context.readResource(EMPTY_ADDRESS, false);
        Set<Resource.ResourceEntry> invmConnectors = serverResource.getChildren(CommonAttributes.IN_VM_CONNECTOR);
        if (invmConnectors.isEmpty()) {
            throw MessagingLogger.ROOT_LOGGER.noInVMConnector();
        }
        Resource.ResourceEntry connectorEntry = invmConnectors.iterator().next();

        Resource connectorResource = context.readResource(PathAddress.pathAddress(connectorEntry.getPathElement()), false);
        ModelNode model = connectorResource.getModel();

        Map<String, Object> params = new HashMap<>(CommonAttributes.PARAMS.unwrap(context, model));
        params.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, InVMTransportDefinition.SERVER_ID.resolveModelAttribute(context, model).asInt());
        TransportConfiguration transportConfiguration = new TransportConfiguration(InVMConnectorFactory.class.getName(), params);
        return transportConfiguration;
    }
}
