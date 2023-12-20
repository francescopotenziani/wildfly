/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.clustering.web.deployment;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.wildfly.clustering.web.infinispan.session.InfinispanSessionManagementConfiguration;
import org.wildfly.clustering.web.service.routing.RouteLocatorServiceConfiguratorFactory;
import org.wildfly.clustering.web.session.DistributableSessionManagementConfiguration;
import org.wildfly.extension.clustering.web.routing.LocalRouteLocatorServiceConfiguratorFactory;
import org.wildfly.extension.clustering.web.routing.NullRouteLocatorServiceConfiguratorFactory;
import org.wildfly.extension.clustering.web.routing.infinispan.PrimaryOwnerRouteLocatorServiceConfiguratorFactory;
import org.wildfly.extension.clustering.web.routing.infinispan.RankedRouteLocatorServiceConfiguratorFactory;
import org.wildfly.extension.clustering.web.session.hotrod.HotRodSessionManagementProvider;
import org.wildfly.extension.clustering.web.session.infinispan.InfinispanSessionManagementProvider;

/**
 * Parser for both jboss-all.xml distributable-web namespace parsing its standalone deployment descriptor counterpart.
 * @author Paul Ferraro
 */
public class DistributableWebDeploymentXMLReader implements XMLElementReader<MutableDistributableWebDeploymentConfiguration> {

    private static final String SESSION_MANAGEMENT = "session-management";
    private static final String NAME = "name";
    private static final String HOTROD_SESSION_MANAGEMENT = "hotrod-session-management";
    private static final String INFINISPAN_SESSION_MANAGEMENT = "infinispan-session-management";
    private static final String REMOTE_CACHE_CONTAINER = "remote-cache-container";
    private static final String CACHE_CONFIGURATION = "cache-configuration";
    private static final String CACHE_CONTAINER = "cache-container";
    private static final String CACHE = "cache";
    private static final String GRANULARITY = "granularity";
    private static final String MARSHALLER = "marshaller";
    private static final String NO_AFFINITY = "no-affinity";
    private static final String LOCAL_AFFINITY = "local-affinity";
    private static final String PRIMARY_OWNER_AFFINITY = "primary-owner-affinity";
    private static final String RANKED_AFFINITY = "ranked-affinity";
    private static final String DELIMITER = "delimiter";
    private static final String MAX_ROUTES = "max-routes";
    private static final String IMMUTABLE_CLASS = "immutable-class";
    private static final String EXPIRATION_THREAD_POOL_SIZE = "expiration-thread-pool-size";

    private final DistributableWebDeploymentSchema schema;

    public DistributableWebDeploymentXMLReader(DistributableWebDeploymentSchema schema) {
        this.schema = schema;
    }

    @Override
    public void readElement(XMLExtendedStreamReader reader, MutableDistributableWebDeploymentConfiguration configuration) throws XMLStreamException {
        ParseUtils.requireNoAttributes(reader);

        Set<String> names = new TreeSet<>();
        names.add(SESSION_MANAGEMENT);
        names.add(HOTROD_SESSION_MANAGEMENT);
        names.add(INFINISPAN_SESSION_MANAGEMENT);

        if (!reader.hasNext() || reader.nextTag() == XMLStreamConstants.END_ELEMENT) {
            throw ParseUtils.missingOneOf(reader, names);
        }

        switch (reader.getLocalName()) {
            case SESSION_MANAGEMENT: {
                this.readSessionManagement(reader, configuration);
                break;
            }
            case HOTROD_SESSION_MANAGEMENT: {
                MutableHotRodSessionManagementConfiguration config = new MutableHotRodSessionManagementConfiguration(configuration);
                configuration.setSessionManagement(new HotRodSessionManagementProvider(config));
                this.readHotRodSessionManagement(reader, config);
                break;
            }
            case INFINISPAN_SESSION_MANAGEMENT: {
                MutableInfinispanSessionManagementConfiguration config = new MutableInfinispanSessionManagementConfiguration(configuration);
                RouteLocatorServiceConfiguratorFactory<InfinispanSessionManagementConfiguration<DeploymentUnit>> factory = this.readInfinispanSessionManagement(reader, config, configuration);
                configuration.setSessionManagement(new InfinispanSessionManagementProvider(config, factory));
                break;
            }
            default: {
                throw ParseUtils.unexpectedElement(reader, names);
            }
        }

        if (reader.hasNext() && (reader.nextTag() != XMLStreamConstants.END_ELEMENT)) {
            throw ParseUtils.unexpectedElement(reader);
        }
    }

    private void readSessionManagement(XMLExtendedStreamReader reader, MutableDistributableWebDeploymentConfiguration configuration) throws XMLStreamException {

        for (int i = 0; i < reader.getAttributeCount(); ++i) {
            String name = reader.getAttributeLocalName(i);
            String value = reader.getAttributeValue(i);

            switch (name) {
                case NAME: {
                    configuration.setSessionManagementName(value);
                    break;
                }
                default: {
                    throw ParseUtils.unexpectedAttribute(reader, i);
                }
            }
        }

        this.readImmutability(reader, configuration);
    }

    @SuppressWarnings("static-method")
    private void readSessionManagementAttribute(XMLExtendedStreamReader reader, int index, MutableSessionManagementConfiguration configuration) throws XMLStreamException {
        String value = reader.getAttributeValue(index);

        switch (reader.getAttributeLocalName(index)) {
            case GRANULARITY: {
                try {
                    configuration.setSessionGranularity(value);
                } catch (IllegalArgumentException e) {
                    throw ParseUtils.invalidAttributeValue(reader, index);
                }
                break;
            }
            case MARSHALLER: {
                try {
                    configuration.setMarshallerFactory(value);
                } catch (IllegalArgumentException e) {
                    throw ParseUtils.invalidAttributeValue(reader, index);
                }
                break;
            }
            default: {
                throw ParseUtils.unexpectedAttribute(reader, index);
            }
        }
    }

    private RouteLocatorServiceConfiguratorFactory<InfinispanSessionManagementConfiguration<DeploymentUnit>> readInfinispanSessionManagement(XMLExtendedStreamReader reader, MutableInfinispanSessionManagementConfiguration configuration, Consumer<String> accumulator) throws XMLStreamException {

        Set<String> required = new TreeSet<>(Arrays.asList(CACHE_CONTAINER, GRANULARITY));

        for (int i = 0; i < reader.getAttributeCount(); ++i) {
            String name = reader.getAttributeLocalName(i);
            String value = reader.getAttributeValue(i);
            required.remove(name);

            switch (name) {
                case CACHE_CONTAINER: {
                    configuration.setContainerName(value);
                    break;
                }
                case CACHE: {
                    configuration.setCacheName(value);
                    break;
                }
                default: {
                    this.readSessionManagementAttribute(reader, i, configuration);
                }
            }
        }

        if (!required.isEmpty()) {
            ParseUtils.requireAttributes(reader, required.toArray(new String[required.size()]));
        }

        RouteLocatorServiceConfiguratorFactory<InfinispanSessionManagementConfiguration<DeploymentUnit>> affinityFactory = this.readInfinispanAffinity(reader);

        this.readImmutability(reader, accumulator);

        return affinityFactory;
    }

    private RouteLocatorServiceConfiguratorFactory<InfinispanSessionManagementConfiguration<DeploymentUnit>> readInfinispanAffinity(XMLExtendedStreamReader reader) throws XMLStreamException {
        if (!reader.hasNext() || reader.nextTag() == XMLStreamConstants.END_ELEMENT) {
            throw ParseUtils.missingRequiredElement(reader, new TreeSet<>(Arrays.asList(NO_AFFINITY, LOCAL_AFFINITY, PRIMARY_OWNER_AFFINITY)));
        }
        switch (reader.getLocalName()) {
            case PRIMARY_OWNER_AFFINITY: {
                ParseUtils.requireNoContent(reader);
                return new PrimaryOwnerRouteLocatorServiceConfiguratorFactory<>();
            }
            case RANKED_AFFINITY: {
                if (this.schema.since(DistributableWebDeploymentSchema.VERSION_2_0)) {
                    MutableRankedRoutingConfiguration config = new MutableRankedRoutingConfiguration();
                    for (int i = 0; i < reader.getAttributeCount(); ++i) {
                        String value = reader.getAttributeValue(i);

                        switch (reader.getAttributeLocalName(i)) {
                            case DELIMITER: {
                                config.setDelimiter(value);
                                break;
                            }
                            case MAX_ROUTES: {
                                config.setMaxRoutes(Integer.parseInt(value));
                                break;
                            }
                            default: {
                                throw ParseUtils.unexpectedAttribute(reader, i);
                            }
                        }
                    }
                    ParseUtils.requireNoContent(reader);
                    return new RankedRouteLocatorServiceConfiguratorFactory<>(config);
                }
            }
            default: {
                return this.readAffinity(reader);
            }
        }
    }

    private RouteLocatorServiceConfiguratorFactory<DistributableSessionManagementConfiguration<DeploymentUnit>> readHotRodSessionManagement(XMLExtendedStreamReader reader, MutableHotRodSessionManagementConfiguration configuration) throws XMLStreamException {

        Set<String> required = new TreeSet<>(Arrays.asList(REMOTE_CACHE_CONTAINER, GRANULARITY));

        for (int i = 0; i < reader.getAttributeCount(); ++i) {
            String localName = reader.getAttributeLocalName(i);
            String value = reader.getAttributeValue(i);
            required.remove(localName);

            switch (localName) {
                case REMOTE_CACHE_CONTAINER: {
                    configuration.setContainerName(value);
                    break;
                }
                case CACHE_CONFIGURATION: {
                    configuration.setConfigurationName(value);
                    break;
                }
                case EXPIRATION_THREAD_POOL_SIZE: {
                    if (this.schema.since(DistributableWebDeploymentSchema.VERSION_4_0)) {
                        configuration.setExpirationThreadPoolSize(Integer.parseInt(value));
                        break;
                    }
                }
                default: {
                    this.readSessionManagementAttribute(reader, i, configuration);
                }
            }
        }

        if (!required.isEmpty()) {
            ParseUtils.requireAttributes(reader, required.toArray(new String[required.size()]));
        }

        if (!reader.hasNext() || reader.nextTag() == XMLStreamConstants.END_ELEMENT) {
            throw ParseUtils.missingRequiredElement(reader, new TreeSet<>(Arrays.asList(NO_AFFINITY, LOCAL_AFFINITY)));
        }

        return this.readAffinity(reader);
    }

    @SuppressWarnings("static-method")
    private <C> RouteLocatorServiceConfiguratorFactory<C> readAffinity(XMLExtendedStreamReader reader) throws XMLStreamException {

        switch (reader.getLocalName()) {
            case NO_AFFINITY: {
                ParseUtils.requireNoAttributes(reader);
                ParseUtils.requireNoContent(reader);
                return new NullRouteLocatorServiceConfiguratorFactory<>();
            }
            case LOCAL_AFFINITY: {
                ParseUtils.requireNoAttributes(reader);
                ParseUtils.requireNoContent(reader);
                return new LocalRouteLocatorServiceConfiguratorFactory<>();
            }
            default: {
                throw ParseUtils.unexpectedElement(reader);
            }
        }
    }

    @SuppressWarnings("static-method")
    private void readImmutability(XMLExtendedStreamReader reader, Consumer<String> accumulator) throws XMLStreamException {
        while (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT) {
            switch (reader.getLocalName()) {
                case IMMUTABLE_CLASS: {
                    ParseUtils.requireNoAttributes(reader);
                    accumulator.accept(reader.getElementText());
                    break;
                }
                default: {
                    throw ParseUtils.unexpectedElement(reader);
                }
            }
        }
    }
}
