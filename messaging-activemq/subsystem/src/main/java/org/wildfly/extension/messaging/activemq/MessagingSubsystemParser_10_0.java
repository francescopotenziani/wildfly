/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.messaging.activemq;

import static org.jboss.as.controller.PathElement.pathElement;
import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.ACCEPTOR;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.CONNECTOR;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.IN_VM_ACCEPTOR;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.IN_VM_CONNECTOR;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.REMOTE_ACCEPTOR;
import static org.wildfly.extension.messaging.activemq.CommonAttributes.REMOTE_CONNECTOR;
import static org.wildfly.extension.messaging.activemq.MessagingExtension.CONFIGURATION_MASTER_PATH;
import static org.wildfly.extension.messaging.activemq.MessagingExtension.CONFIGURATION_SLAVE_PATH;
import static org.wildfly.extension.messaging.activemq.MessagingExtension.REPLICATION_MASTER_PATH;
import static org.wildfly.extension.messaging.activemq.MessagingExtension.SHARED_STORE_MASTER_PATH;
import static org.wildfly.extension.messaging.activemq.MessagingExtension.SHARED_STORE_SLAVE_PATH;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLDescription.PersistentResourceXMLBuilder;
import org.jboss.as.controller.PersistentResourceXMLParser;
import org.wildfly.extension.messaging.activemq.ha.HAAttributes;
import org.wildfly.extension.messaging.activemq.ha.ScaleDownAttributes;
import org.wildfly.extension.messaging.activemq.jms.ConnectionFactoryAttributes;
import org.wildfly.extension.messaging.activemq.jms.bridge.JMSBridgeDefinition;
import org.wildfly.extension.messaging.activemq.jms.legacy.LegacyConnectionFactoryDefinition;

/**
 * Parser and Marshaller for messaging-activemq's {@link #NAMESPACE}.
 *
 * <em>All resources and attributes must be listed explicitly and not through any collections.</em>
 * This ensures that if the resource definitions change in later version (e.g. a new attribute is added),
 * this will have no impact on parsing this specific version of the subsystem.
 *
 * @author Paul Ferraro
 */
public class MessagingSubsystemParser_10_0 extends PersistentResourceXMLParser {

    static final String NAMESPACE = "urn:jboss:domain:messaging-activemq:10.0";

    @Override
    public PersistentResourceXMLDescription getParserDescription() {

        final PersistentResourceXMLBuilder jgroupDiscoveryGroup = builder(JGroupsDiscoveryGroupDefinition.PATH)
                .addAttributes(
                        DiscoveryGroupDefinition.JGROUPS_CHANNEL_FACTORY,
                        DiscoveryGroupDefinition.JGROUPS_CHANNEL,
                        CommonAttributes.JGROUPS_CLUSTER,
                        DiscoveryGroupDefinition.REFRESH_TIMEOUT,
                        DiscoveryGroupDefinition.INITIAL_WAIT_TIMEOUT);

        final PersistentResourceXMLBuilder socketDiscoveryGroup = builder(SocketDiscoveryGroupDefinition.PATH)
                .addAttributes(
                        CommonAttributes.SOCKET_BINDING,
                        DiscoveryGroupDefinition.REFRESH_TIMEOUT,
                        DiscoveryGroupDefinition.INITIAL_WAIT_TIMEOUT);

        final PersistentResourceXMLBuilder remoteConnector = builder(pathElement(REMOTE_CONNECTOR))
                .addAttributes(
                        RemoteTransportDefinition.SOCKET_BINDING,
                        CommonAttributes.PARAMS);

        final PersistentResourceXMLBuilder httpConnector = builder(MessagingExtension.HTTP_CONNECTOR_PATH)
                .addAttributes(
                        HTTPConnectorDefinition.SOCKET_BINDING,
                        HTTPConnectorDefinition.ENDPOINT,
                        HTTPConnectorDefinition.SERVER_NAME,
                        CommonAttributes.PARAMS);

        final PersistentResourceXMLBuilder invmConnector = builder(pathElement(IN_VM_CONNECTOR))
                .addAttributes(
                        InVMTransportDefinition.SERVER_ID,
                        CommonAttributes.PARAMS);

        final PersistentResourceXMLBuilder connector = builder(pathElement(CONNECTOR))
                .addAttributes(
                        GenericTransportDefinition.SOCKET_BINDING,
                        CommonAttributes.FACTORY_CLASS,
                        CommonAttributes.PARAMS);

        return builder(MessagingExtension.SUBSYSTEM_PATH, NAMESPACE)
                .addAttributes(
                        MessagingSubsystemRootResourceDefinition.GLOBAL_CLIENT_THREAD_POOL_MAX_SIZE,
                        MessagingSubsystemRootResourceDefinition.GLOBAL_CLIENT_SCHEDULED_THREAD_POOL_MAX_SIZE)
                .addChild(httpConnector)
                .addChild(remoteConnector)
                .addChild(invmConnector)
                .addChild(connector)
                .addChild(jgroupDiscoveryGroup)
                .addChild(socketDiscoveryGroup)
                .addChild(builder(MessagingExtension.CONNECTION_FACTORY_PATH)
                        .addAttributes(
                                CommonAttributes.HA,
                                ConnectionFactoryAttributes.Regular.FACTORY_TYPE,
                                ConnectionFactoryAttributes.Common.DISCOVERY_GROUP,
                                ConnectionFactoryAttributes.Common.CONNECTORS,
                                ConnectionFactoryAttributes.Common.ENTRIES,
                                ConnectionFactoryAttributes.External.ENABLE_AMQ1_PREFIX,
                                ConnectionFactoryAttributes.Common.USE_TOPOLOGY
                        ))
                .addChild(createPooledConnectionFactory(true))
                .addChild(builder(MessagingExtension.EXTERNAL_JMS_QUEUE_PATH)
                        .addAttributes(
                                ConnectionFactoryAttributes.Common.ENTRIES
                        ))
                .addChild(builder(MessagingExtension.EXTERNAL_JMS_TOPIC_PATH)
                        .addAttributes(
                                ConnectionFactoryAttributes.Common.ENTRIES
                        ))
                .addChild(builder(MessagingExtension.SERVER_PATH)
                                .addAttributes(// no attribute groups
                                        ServerDefinition.PERSISTENCE_ENABLED,
                                        ServerDefinition.PERSIST_ID_CACHE,
                                        ServerDefinition.PERSIST_DELIVERY_COUNT_BEFORE_DELIVERY,
                                        ServerDefinition.ID_CACHE_SIZE,
                                        ServerDefinition.PAGE_MAX_CONCURRENT_IO,
                                        ServerDefinition.SCHEDULED_THREAD_POOL_MAX_SIZE,
                                        ServerDefinition.THREAD_POOL_MAX_SIZE,
                                        ServerDefinition.WILD_CARD_ROUTING_ENABLED,
                                        ServerDefinition.CONNECTION_TTL_OVERRIDE,
                                        ServerDefinition.ASYNC_CONNECTION_EXECUTION_ENABLED,
                                        // security
                                        ServerDefinition.SECURITY_ENABLED,
                                        ServerDefinition.SECURITY_DOMAIN,
                                        ServerDefinition.ELYTRON_DOMAIN,
                                        ServerDefinition.SECURITY_INVALIDATION_INTERVAL,
                                        ServerDefinition.OVERRIDE_IN_VM_SECURITY,
                                        // cluster
                                        ServerDefinition.CLUSTER_USER,
                                        ServerDefinition.CLUSTER_PASSWORD,
                                        ServerDefinition.CREDENTIAL_REFERENCE,
                                        // management
                                        ServerDefinition.MANAGEMENT_ADDRESS,
                                        ServerDefinition.MANAGEMENT_NOTIFICATION_ADDRESS,
                                        ServerDefinition.JMX_MANAGEMENT_ENABLED,
                                        ServerDefinition.JMX_DOMAIN,
                                        // journal
                                        ServerDefinition.JOURNAL_TYPE,
                                        ServerDefinition.JOURNAL_BUFFER_TIMEOUT,
                                        ServerDefinition.JOURNAL_BUFFER_SIZE,
                                        ServerDefinition.JOURNAL_SYNC_TRANSACTIONAL,
                                        ServerDefinition.JOURNAL_SYNC_NON_TRANSACTIONAL,
                                        ServerDefinition.LOG_JOURNAL_WRITE_RATE,
                                        ServerDefinition.JOURNAL_FILE_SIZE,
                                        ServerDefinition.JOURNAL_MIN_FILES,
                                        ServerDefinition.JOURNAL_POOL_FILES,
                                        ServerDefinition.JOURNAL_FILE_OPEN_TIMEOUT,
                                        ServerDefinition.JOURNAL_COMPACT_PERCENTAGE,
                                        ServerDefinition.JOURNAL_COMPACT_MIN_FILES,
                                        ServerDefinition.JOURNAL_MAX_IO,
                                        ServerDefinition.CREATE_BINDINGS_DIR,
                                        ServerDefinition.CREATE_JOURNAL_DIR,
                                        ServerDefinition.JOURNAL_DATASOURCE,
                                        ServerDefinition.JOURNAL_MESSAGES_TABLE,
                                        ServerDefinition.JOURNAL_BINDINGS_TABLE,
                                        ServerDefinition.JOURNAL_JMS_BINDINGS_TABLE,
                                        ServerDefinition.JOURNAL_LARGE_MESSAGES_TABLE,
                                        ServerDefinition.JOURNAL_PAGE_STORE_TABLE,
                                        ServerDefinition.JOURNAL_NODE_MANAGER_STORE_TABLE,
                                        ServerDefinition.JOURNAL_DATABASE,
                                        ServerDefinition.JOURNAL_JDBC_LOCK_EXPIRATION,
                                        ServerDefinition.JOURNAL_JDBC_LOCK_RENEW_PERIOD,
                                        ServerDefinition.JOURNAL_JDBC_NETWORK_TIMEOUT,
                                        ServerDefinition.GLOBAL_MAX_DISK_USAGE,
                                        ServerDefinition.DISK_SCAN_PERIOD,
                                        ServerDefinition.GLOBAL_MAX_MEMORY_SIZE,
                                        // statistics
                                        ServerDefinition.STATISTICS_ENABLED,
                                        ServerDefinition.MESSAGE_COUNTER_SAMPLE_PERIOD,
                                        ServerDefinition.MESSAGE_COUNTER_MAX_DAY_HISTORY,
                                        // transaction
                                        ServerDefinition.TRANSACTION_TIMEOUT,
                                        ServerDefinition.TRANSACTION_TIMEOUT_SCAN_PERIOD,
                                        // message expiry
                                        ServerDefinition.MESSAGE_EXPIRY_SCAN_PERIOD,
                                        ServerDefinition.MESSAGE_EXPIRY_THREAD_PRIORITY,
                                        // debug
                                        ServerDefinition.PERF_BLAST_PAGES,
                                        ServerDefinition.RUN_SYNC_SPEED_TEST,
                                        ServerDefinition.SERVER_DUMP_INTERVAL,
                                        ServerDefinition.MEMORY_MEASURE_INTERVAL,
                                        ServerDefinition.MEMORY_WARNING_THRESHOLD,
                                        CommonAttributes.INCOMING_INTERCEPTORS,
                                        CommonAttributes.OUTGOING_INTERCEPTORS)
                                .addChild(
                                        builder(MessagingExtension.LIVE_ONLY_PATH)
                                                .addAttributes(
                                                        ScaleDownAttributes.SCALE_DOWN,
                                                        ScaleDownAttributes.SCALE_DOWN_CLUSTER_NAME,
                                                        ScaleDownAttributes.SCALE_DOWN_GROUP_NAME,
                                                        ScaleDownAttributes.SCALE_DOWN_DISCOVERY_GROUP,
                                                        ScaleDownAttributes.SCALE_DOWN_CONNECTORS))
                                .addChild(builder(REPLICATION_MASTER_PATH)
                                                .addAttributes(
                                                        HAAttributes.CLUSTER_NAME,
                                                        HAAttributes.GROUP_NAME,
                                                        HAAttributes.CHECK_FOR_LIVE_SERVER,
                                                        HAAttributes.INITIAL_REPLICATION_SYNC_TIMEOUT))
                                .addChild(builder(MessagingExtension.REPLICATION_SLAVE_PATH)
                                                .addAttributes(
                                                        HAAttributes.CLUSTER_NAME,
                                                        HAAttributes.GROUP_NAME,
                                                        HAAttributes.ALLOW_FAILBACK,
                                                        HAAttributes.INITIAL_REPLICATION_SYNC_TIMEOUT,
                                                        HAAttributes.MAX_SAVED_REPLICATED_JOURNAL_SIZE,
                                                        HAAttributes.RESTART_BACKUP,
                                                        ScaleDownAttributes.SCALE_DOWN,
                                                        ScaleDownAttributes.SCALE_DOWN_CLUSTER_NAME,
                                                        ScaleDownAttributes.SCALE_DOWN_GROUP_NAME,
                                                        ScaleDownAttributes.SCALE_DOWN_DISCOVERY_GROUP,
                                                        ScaleDownAttributes.SCALE_DOWN_CONNECTORS))
                                .addChild(builder(MessagingExtension.REPLICATION_COLOCATED_PATH)
                                                .addAttributes(
                                                        HAAttributes.REQUEST_BACKUP,
                                                        HAAttributes.BACKUP_REQUEST_RETRIES,
                                                        HAAttributes.BACKUP_REQUEST_RETRY_INTERVAL,
                                                        HAAttributes.MAX_BACKUPS,
                                                        HAAttributes.BACKUP_PORT_OFFSET,
                                                        HAAttributes.EXCLUDED_CONNECTORS)
                                                .addChild(builder(MessagingExtension.CONFIGURATION_MASTER_PATH)
                                                                .addAttributes(
                                                                        HAAttributes.CLUSTER_NAME,
                                                                        HAAttributes.GROUP_NAME,
                                                                        HAAttributes.CHECK_FOR_LIVE_SERVER,
                                                                        HAAttributes.INITIAL_REPLICATION_SYNC_TIMEOUT))
                                                .addChild(builder(CONFIGURATION_SLAVE_PATH)
                                                                .addAttributes(
                                                                        HAAttributes.CLUSTER_NAME,
                                                                        HAAttributes.GROUP_NAME,
                                                                        HAAttributes.ALLOW_FAILBACK,
                                                                        HAAttributes.INITIAL_REPLICATION_SYNC_TIMEOUT,
                                                                        HAAttributes.MAX_SAVED_REPLICATED_JOURNAL_SIZE,
                                                                        HAAttributes.RESTART_BACKUP,
                                                                        ScaleDownAttributes.SCALE_DOWN,
                                                                        ScaleDownAttributes.SCALE_DOWN_CLUSTER_NAME,
                                                                        ScaleDownAttributes.SCALE_DOWN_GROUP_NAME,
                                                                        ScaleDownAttributes.SCALE_DOWN_DISCOVERY_GROUP,
                                                                        ScaleDownAttributes.SCALE_DOWN_CONNECTORS)))
                                .addChild(builder(SHARED_STORE_MASTER_PATH)
                                                .addAttributes(
                                                        HAAttributes.FAILOVER_ON_SERVER_SHUTDOWN))
                                .addChild(builder(SHARED_STORE_SLAVE_PATH)
                                                .addAttributes(
                                                        HAAttributes.ALLOW_FAILBACK,
                                                        HAAttributes.FAILOVER_ON_SERVER_SHUTDOWN,
                                                        HAAttributes.RESTART_BACKUP,
                                                        ScaleDownAttributes.SCALE_DOWN,
                                                        ScaleDownAttributes.SCALE_DOWN_CLUSTER_NAME,
                                                        ScaleDownAttributes.SCALE_DOWN_GROUP_NAME,
                                                        ScaleDownAttributes.SCALE_DOWN_DISCOVERY_GROUP,
                                                        ScaleDownAttributes.SCALE_DOWN_CONNECTORS))
                                .addChild(builder(MessagingExtension.SHARED_STORE_COLOCATED_PATH)
                                                .addAttributes(
                                                        HAAttributes.REQUEST_BACKUP,
                                                        HAAttributes.BACKUP_REQUEST_RETRIES,
                                                        HAAttributes.BACKUP_REQUEST_RETRY_INTERVAL,
                                                        HAAttributes.MAX_BACKUPS,
                                                        HAAttributes.BACKUP_PORT_OFFSET)
                                                .addChild(builder(CONFIGURATION_MASTER_PATH)
                                                                .addAttributes(
                                                                        HAAttributes.FAILOVER_ON_SERVER_SHUTDOWN))
                                                .addChild(builder(CONFIGURATION_SLAVE_PATH)
                                                                .addAttributes(
                                                                        HAAttributes.ALLOW_FAILBACK,
                                                                        HAAttributes.FAILOVER_ON_SERVER_SHUTDOWN,
                                                                        HAAttributes.RESTART_BACKUP,
                                                                        ScaleDownAttributes.SCALE_DOWN,
                                                                        ScaleDownAttributes.SCALE_DOWN_CLUSTER_NAME,
                                                                        ScaleDownAttributes.SCALE_DOWN_GROUP_NAME,
                                                                        ScaleDownAttributes.SCALE_DOWN_DISCOVERY_GROUP,
                                                                        ScaleDownAttributes.SCALE_DOWN_CONNECTORS)))
                                .addChild(
                                        builder(MessagingExtension.BINDINGS_DIRECTORY_PATH)
                                                .addAttributes(
                                                        PathDefinition.PATHS.get(CommonAttributes.BINDINGS_DIRECTORY),
                                                        PathDefinition.RELATIVE_TO))
                                .addChild(
                                        builder(MessagingExtension.JOURNAL_DIRECTORY_PATH)
                                                .addAttributes(
                                                        PathDefinition.PATHS.get(CommonAttributes.JOURNAL_DIRECTORY),
                                                        PathDefinition.RELATIVE_TO))
                                .addChild(
                                        builder(MessagingExtension.LARGE_MESSAGES_DIRECTORY_PATH)
                                                .addAttributes(
                                                        PathDefinition.PATHS.get(CommonAttributes.LARGE_MESSAGES_DIRECTORY),
                                                        PathDefinition.RELATIVE_TO))
                                .addChild(
                                        builder(MessagingExtension.PAGING_DIRECTORY_PATH)
                                                .addAttributes(
                                                        PathDefinition.PATHS.get(CommonAttributes.PAGING_DIRECTORY),
                                                        PathDefinition.RELATIVE_TO))
                                .addChild(
                                        builder(MessagingExtension.QUEUE_PATH)
                                                .addAttributes(QueueDefinition.ADDRESS,
                                                        CommonAttributes.DURABLE,
                                                        CommonAttributes.FILTER,
                                                        QueueDefinition.ROUTING_TYPE))
                                .addChild(
                                        builder(MessagingExtension.SECURITY_SETTING_PATH)
                                                .addChild(
                                                        builder(MessagingExtension.ROLE_PATH)
                                                                .addAttributes(
                                                                        SecurityRoleDefinition.SEND,
                                                                        SecurityRoleDefinition.CONSUME,
                                                                        SecurityRoleDefinition.CREATE_DURABLE_QUEUE,
                                                                        SecurityRoleDefinition.DELETE_DURABLE_QUEUE,
                                                                        SecurityRoleDefinition.CREATE_NON_DURABLE_QUEUE,
                                                                        SecurityRoleDefinition.DELETE_NON_DURABLE_QUEUE,
                                                                        SecurityRoleDefinition.MANAGE)))
                                .addChild(
                                        builder(MessagingExtension.ADDRESS_SETTING_PATH)
                                                .addAttributes(
                                                        CommonAttributes.DEAD_LETTER_ADDRESS,
                                                        CommonAttributes.EXPIRY_ADDRESS,
                                                        AddressSettingDefinition.EXPIRY_DELAY,
                                                        AddressSettingDefinition.REDELIVERY_DELAY,
                                                        AddressSettingDefinition.REDELIVERY_MULTIPLIER,
                                                        AddressSettingDefinition.MAX_DELIVERY_ATTEMPTS,
                                                        AddressSettingDefinition.MAX_REDELIVERY_DELAY,
                                                        AddressSettingDefinition.MAX_SIZE_BYTES,
                                                        AddressSettingDefinition.PAGE_SIZE_BYTES,
                                                        AddressSettingDefinition.PAGE_MAX_CACHE_SIZE,
                                                        AddressSettingDefinition.ADDRESS_FULL_MESSAGE_POLICY,
                                                        AddressSettingDefinition.MESSAGE_COUNTER_HISTORY_DAY_LIMIT,
                                                        AddressSettingDefinition.LAST_VALUE_QUEUE,
                                                        AddressSettingDefinition.REDISTRIBUTION_DELAY,
                                                        AddressSettingDefinition.SEND_TO_DLA_ON_NO_ROUTE,
                                                        AddressSettingDefinition.SLOW_CONSUMER_CHECK_PERIOD,
                                                        AddressSettingDefinition.SLOW_CONSUMER_POLICY,
                                                        AddressSettingDefinition.SLOW_CONSUMER_THRESHOLD,
                                                        AddressSettingDefinition.AUTO_CREATE_JMS_QUEUES,
                                                        AddressSettingDefinition.AUTO_DELETE_JMS_QUEUES,
                                                        AddressSettingDefinition.AUTO_CREATE_QUEUES,
                                                        AddressSettingDefinition.AUTO_DELETE_QUEUES,
                                                        AddressSettingDefinition.AUTO_CREATE_ADDRESSES,
                                                        AddressSettingDefinition.AUTO_DELETE_ADDRESSES))
                                .addChild(httpConnector)
                                .addChild(remoteConnector)
                                .addChild(invmConnector)
                                .addChild(connector)
                                .addChild(
                                        builder(MessagingExtension.HTTP_ACCEPTOR_PATH)
                                                .addAttributes(
                                                        HTTPAcceptorDefinition.HTTP_LISTENER,
                                                        HTTPAcceptorDefinition.UPGRADE_LEGACY,
                                                        CommonAttributes.PARAMS))
                                .addChild(
                                        builder(pathElement(REMOTE_ACCEPTOR))
                                                .addAttributes(
                                                        RemoteTransportDefinition.SOCKET_BINDING,
                                                        CommonAttributes.PARAMS))
                                .addChild(
                                        builder(pathElement(IN_VM_ACCEPTOR))
                                                .addAttributes(
                                                        InVMTransportDefinition.SERVER_ID,
                                                        CommonAttributes.PARAMS))
                                .addChild(
                                        builder(pathElement(ACCEPTOR))
                                                .addAttributes(
                                                        GenericTransportDefinition.SOCKET_BINDING,
                                                        CommonAttributes.FACTORY_CLASS,
                                                        CommonAttributes.PARAMS))
                                .addChild(
                                        builder(MessagingExtension.JGROUPS_BROADCAST_GROUP_PATH)
                                                .addAttributes(
                                                        BroadcastGroupDefinition.JGROUPS_CHANNEL_FACTORY,
                                                        BroadcastGroupDefinition.JGROUPS_CHANNEL,
                                                        CommonAttributes.JGROUPS_CLUSTER,
                                                        BroadcastGroupDefinition.BROADCAST_PERIOD,
                                                        BroadcastGroupDefinition.CONNECTOR_REFS))
                                .addChild(
                                        builder(MessagingExtension.SOCKET_BROADCAST_GROUP_PATH)
                                                .addAttributes(
                                                        CommonAttributes.SOCKET_BINDING,
                                                        BroadcastGroupDefinition.BROADCAST_PERIOD,
                                                        BroadcastGroupDefinition.CONNECTOR_REFS))
                                .addChild(jgroupDiscoveryGroup)
                                .addChild(socketDiscoveryGroup)
                                .addChild(
                                        builder(MessagingExtension.CLUSTER_CONNECTION_PATH)
                                                .addAttributes(
                                                        ClusterConnectionDefinition.ADDRESS,
                                                        ClusterConnectionDefinition.CONNECTOR_NAME,
                                                        ClusterConnectionDefinition.CHECK_PERIOD,
                                                        ClusterConnectionDefinition.CONNECTION_TTL,
                                                        CommonAttributes.MIN_LARGE_MESSAGE_SIZE,
                                                        CommonAttributes.CALL_TIMEOUT,
                                                        ClusterConnectionDefinition.CALL_FAILOVER_TIMEOUT,
                                                        ClusterConnectionDefinition.RETRY_INTERVAL,
                                                        ClusterConnectionDefinition.RETRY_INTERVAL_MULTIPLIER,
                                                        ClusterConnectionDefinition.MAX_RETRY_INTERVAL,
                                                        ClusterConnectionDefinition.INITIAL_CONNECT_ATTEMPTS,
                                                        ClusterConnectionDefinition.RECONNECT_ATTEMPTS,
                                                        ClusterConnectionDefinition.USE_DUPLICATE_DETECTION,
                                                        ClusterConnectionDefinition.MESSAGE_LOAD_BALANCING_TYPE,
                                                        ClusterConnectionDefinition.MAX_HOPS,
                                                        CommonAttributes.BRIDGE_CONFIRMATION_WINDOW_SIZE,
                                                        ClusterConnectionDefinition.PRODUCER_WINDOW_SIZE,
                                                        ClusterConnectionDefinition.NOTIFICATION_ATTEMPTS,
                                                        ClusterConnectionDefinition.NOTIFICATION_INTERVAL,
                                                        ClusterConnectionDefinition.CONNECTOR_REFS,
                                                        ClusterConnectionDefinition.ALLOW_DIRECT_CONNECTIONS_ONLY,
                                                        ClusterConnectionDefinition.DISCOVERY_GROUP_NAME))
                                .addChild(
                                        builder(MessagingExtension.GROUPING_HANDLER_PATH)
                                                .addAttributes(
                                                        GroupingHandlerDefinition.TYPE,
                                                        GroupingHandlerDefinition.GROUPING_HANDLER_ADDRESS,
                                                        GroupingHandlerDefinition.TIMEOUT,
                                                        GroupingHandlerDefinition.GROUP_TIMEOUT,
                                                        GroupingHandlerDefinition.REAPER_PERIOD))
                                .addChild(
                                        builder(DivertDefinition.PATH)
                                                .addAttributes(
                                                        DivertDefinition.ROUTING_NAME,
                                                        DivertDefinition.ADDRESS,
                                                        DivertDefinition.FORWARDING_ADDRESS,
                                                        CommonAttributes.FILTER,
                                                        CommonAttributes.TRANSFORMER_CLASS_NAME,
                                                        DivertDefinition.EXCLUSIVE))
                                .addChild(
                                        builder(MessagingExtension.BRIDGE_PATH)
                                                .addAttributes(
                                                        BridgeDefinition.QUEUE_NAME,
                                                        BridgeDefinition.FORWARDING_ADDRESS,
                                                        CommonAttributes.HA,
                                                        CommonAttributes.FILTER,
                                                        CommonAttributes.TRANSFORMER_CLASS_NAME,
                                                        CommonAttributes.MIN_LARGE_MESSAGE_SIZE,
                                                        CommonAttributes.CHECK_PERIOD,
                                                        CommonAttributes.CONNECTION_TTL,
                                                        CommonAttributes.RETRY_INTERVAL,
                                                        CommonAttributes.RETRY_INTERVAL_MULTIPLIER,
                                                        CommonAttributes.MAX_RETRY_INTERVAL,
                                                        BridgeDefinition.INITIAL_CONNECT_ATTEMPTS,
                                                        BridgeDefinition.RECONNECT_ATTEMPTS,
                                                        BridgeDefinition.RECONNECT_ATTEMPTS_ON_SAME_NODE,
                                                        BridgeDefinition.USE_DUPLICATE_DETECTION,
                                                        CommonAttributes.BRIDGE_CONFIRMATION_WINDOW_SIZE,
                                                        BridgeDefinition.PRODUCER_WINDOW_SIZE,
                                                        BridgeDefinition.USER,
                                                        BridgeDefinition.PASSWORD,
                                                        BridgeDefinition.CREDENTIAL_REFERENCE,
                                                        BridgeDefinition.CONNECTOR_REFS,
                                                        BridgeDefinition.DISCOVERY_GROUP_NAME))
                                .addChild(
                                        builder(MessagingExtension.CONNECTOR_SERVICE_PATH)
                                                .addAttributes(
                                                        CommonAttributes.FACTORY_CLASS,
                                                        CommonAttributes.PARAMS))
                                .addChild(
                                        builder(MessagingExtension.JMS_QUEUE_PATH)
                                                .addAttributes(
                                                        CommonAttributes.DESTINATION_ENTRIES,
                                                        CommonAttributes.SELECTOR,
                                                        CommonAttributes.DURABLE,
                                                        CommonAttributes.LEGACY_ENTRIES))
                                .addChild(
                                        builder(MessagingExtension.JMS_TOPIC_PATH)
                                                .addAttributes(
                                                        CommonAttributes.DESTINATION_ENTRIES,
                                                        CommonAttributes.LEGACY_ENTRIES))
                                .addChild(
                                        builder(MessagingExtension.CONNECTION_FACTORY_PATH)
                                                .addAttributes(
                                                        ConnectionFactoryAttributes.Common.ENTRIES,
                                                        // common
                                                        ConnectionFactoryAttributes.Common.DISCOVERY_GROUP,
                                                        ConnectionFactoryAttributes.Common.CONNECTORS,
                                                        CommonAttributes.HA,
                                                        ConnectionFactoryAttributes.Common.CLIENT_FAILURE_CHECK_PERIOD,
                                                        ConnectionFactoryAttributes.Common.CONNECTION_TTL,
                                                        CommonAttributes.CALL_TIMEOUT,
                                                        CommonAttributes.CALL_FAILOVER_TIMEOUT,
                                                        ConnectionFactoryAttributes.Common.CONSUMER_WINDOW_SIZE,
                                                        ConnectionFactoryAttributes.Common.CONSUMER_MAX_RATE,
                                                        ConnectionFactoryAttributes.Common.CONFIRMATION_WINDOW_SIZE,
                                                        ConnectionFactoryAttributes.Common.PRODUCER_WINDOW_SIZE,
                                                        ConnectionFactoryAttributes.Common.PRODUCER_MAX_RATE,
                                                        ConnectionFactoryAttributes.Common.PROTOCOL_MANAGER_FACTORY,
                                                        ConnectionFactoryAttributes.Common.COMPRESS_LARGE_MESSAGES,
                                                        ConnectionFactoryAttributes.Common.CACHE_LARGE_MESSAGE_CLIENT,
                                                        CommonAttributes.MIN_LARGE_MESSAGE_SIZE,
                                                        CommonAttributes.CLIENT_ID,
                                                        ConnectionFactoryAttributes.Common.DUPS_OK_BATCH_SIZE,
                                                        ConnectionFactoryAttributes.Common.TRANSACTION_BATCH_SIZE,
                                                        ConnectionFactoryAttributes.Common.BLOCK_ON_ACKNOWLEDGE,
                                                        ConnectionFactoryAttributes.Common.BLOCK_ON_NON_DURABLE_SEND,
                                                        ConnectionFactoryAttributes.Common.BLOCK_ON_DURABLE_SEND,
                                                        ConnectionFactoryAttributes.Common.AUTO_GROUP,
                                                        ConnectionFactoryAttributes.Common.PRE_ACKNOWLEDGE,
                                                        ConnectionFactoryAttributes.Common.RETRY_INTERVAL,
                                                        ConnectionFactoryAttributes.Common.RETRY_INTERVAL_MULTIPLIER,
                                                        CommonAttributes.MAX_RETRY_INTERVAL,
                                                        ConnectionFactoryAttributes.Common.RECONNECT_ATTEMPTS,
                                                        ConnectionFactoryAttributes.Common.FAILOVER_ON_INITIAL_CONNECTION,
                                                        ConnectionFactoryAttributes.Common.CONNECTION_LOAD_BALANCING_CLASS_NAME,
                                                        ConnectionFactoryAttributes.Common.USE_GLOBAL_POOLS,
                                                        ConnectionFactoryAttributes.Common.SCHEDULED_THREAD_POOL_MAX_SIZE,
                                                        ConnectionFactoryAttributes.Common.THREAD_POOL_MAX_SIZE,
                                                        ConnectionFactoryAttributes.Common.GROUP_ID,
                                                        ConnectionFactoryAttributes.Common.DESERIALIZATION_BLACKLIST,
                                                        ConnectionFactoryAttributes.Common.DESERIALIZATION_WHITELIST,
                                                        ConnectionFactoryAttributes.Common.INITIAL_MESSAGE_PACKET_SIZE,
                                                        ConnectionFactoryAttributes.Regular.FACTORY_TYPE,
                                                        ConnectionFactoryAttributes.Common.USE_TOPOLOGY))
                                .addChild(
                                        builder(MessagingExtension.LEGACY_CONNECTION_FACTORY_PATH)
                                                .addAttributes(
                                                        LegacyConnectionFactoryDefinition.ENTRIES,
                                                        LegacyConnectionFactoryDefinition.DISCOVERY_GROUP,
                                                        LegacyConnectionFactoryDefinition.CONNECTORS,
                                                        LegacyConnectionFactoryDefinition.AUTO_GROUP,
                                                        LegacyConnectionFactoryDefinition.BLOCK_ON_ACKNOWLEDGE,
                                                        LegacyConnectionFactoryDefinition.BLOCK_ON_DURABLE_SEND,
                                                        LegacyConnectionFactoryDefinition.BLOCK_ON_NON_DURABLE_SEND,
                                                        CommonAttributes.CALL_TIMEOUT,
                                                        CommonAttributes.CALL_FAILOVER_TIMEOUT,
                                                        LegacyConnectionFactoryDefinition.CACHE_LARGE_MESSAGE_CLIENT,
                                                        LegacyConnectionFactoryDefinition.CLIENT_FAILURE_CHECK_PERIOD,
                                                        CommonAttributes.CLIENT_ID,
                                                        LegacyConnectionFactoryDefinition.COMPRESS_LARGE_MESSAGES,
                                                        LegacyConnectionFactoryDefinition.CONFIRMATION_WINDOW_SIZE,
                                                        LegacyConnectionFactoryDefinition.CONNECTION_LOAD_BALANCING_CLASS_NAME,
                                                        LegacyConnectionFactoryDefinition.CONNECTION_TTL,
                                                        LegacyConnectionFactoryDefinition.CONSUMER_MAX_RATE,
                                                        LegacyConnectionFactoryDefinition.CONSUMER_WINDOW_SIZE,
                                                        LegacyConnectionFactoryDefinition.DUPS_OK_BATCH_SIZE,
                                                        LegacyConnectionFactoryDefinition.FACTORY_TYPE,
                                                        LegacyConnectionFactoryDefinition.FAILOVER_ON_INITIAL_CONNECTION,
                                                        LegacyConnectionFactoryDefinition.GROUP_ID,
                                                        LegacyConnectionFactoryDefinition.INITIAL_CONNECT_ATTEMPTS,
                                                        LegacyConnectionFactoryDefinition.INITIAL_MESSAGE_PACKET_SIZE,
                                                        LegacyConnectionFactoryDefinition.HA,
                                                        LegacyConnectionFactoryDefinition.MAX_RETRY_INTERVAL,
                                                        LegacyConnectionFactoryDefinition.MIN_LARGE_MESSAGE_SIZE,
                                                        LegacyConnectionFactoryDefinition.PRE_ACKNOWLEDGE,
                                                        LegacyConnectionFactoryDefinition.PRODUCER_MAX_RATE,
                                                        LegacyConnectionFactoryDefinition.PRODUCER_WINDOW_SIZE,
                                                        LegacyConnectionFactoryDefinition.RECONNECT_ATTEMPTS,
                                                        LegacyConnectionFactoryDefinition.RETRY_INTERVAL,
                                                        LegacyConnectionFactoryDefinition.RETRY_INTERVAL_MULTIPLIER,
                                                        LegacyConnectionFactoryDefinition.SCHEDULED_THREAD_POOL_MAX_SIZE,
                                                        LegacyConnectionFactoryDefinition.THREAD_POOL_MAX_SIZE,
                                                        LegacyConnectionFactoryDefinition.TRANSACTION_BATCH_SIZE,
                                                        LegacyConnectionFactoryDefinition.USE_GLOBAL_POOLS))
                                .addChild(createPooledConnectionFactory(false)))
                .addChild(
                        builder(MessagingExtension.JMS_BRIDGE_PATH)
                                .addAttributes(
                                        JMSBridgeDefinition.MODULE,
                                        JMSBridgeDefinition.QUALITY_OF_SERVICE,
                                        JMSBridgeDefinition.FAILURE_RETRY_INTERVAL,
                                        JMSBridgeDefinition.MAX_RETRIES,
                                        JMSBridgeDefinition.MAX_BATCH_SIZE,
                                        JMSBridgeDefinition.MAX_BATCH_TIME,
                                        CommonAttributes.SELECTOR,
                                        JMSBridgeDefinition.SUBSCRIPTION_NAME,
                                        CommonAttributes.CLIENT_ID,
                                        JMSBridgeDefinition.ADD_MESSAGE_ID_IN_HEADER,
                                        JMSBridgeDefinition.SOURCE_CONNECTION_FACTORY,
                                        JMSBridgeDefinition.SOURCE_DESTINATION,
                                        JMSBridgeDefinition.SOURCE_USER,
                                        JMSBridgeDefinition.SOURCE_PASSWORD,
                                        JMSBridgeDefinition.SOURCE_CREDENTIAL_REFERENCE,
                                        JMSBridgeDefinition.TARGET_CONNECTION_FACTORY,
                                        JMSBridgeDefinition.TARGET_DESTINATION,
                                        JMSBridgeDefinition.TARGET_USER,
                                        JMSBridgeDefinition.TARGET_PASSWORD,
                                        JMSBridgeDefinition.TARGET_CREDENTIAL_REFERENCE,
                                        JMSBridgeDefinition.SOURCE_CONTEXT,
                                        JMSBridgeDefinition.TARGET_CONTEXT))
                .build();
    }

    private PersistentResourceXMLBuilder createPooledConnectionFactory(boolean external) {
        PersistentResourceXMLBuilder builder = builder(MessagingExtension.POOLED_CONNECTION_FACTORY_PATH)
                .addAttributes(
                        ConnectionFactoryAttributes.Common.ENTRIES,
                        // common
                        ConnectionFactoryAttributes.Common.DISCOVERY_GROUP,
                        ConnectionFactoryAttributes.Common.CONNECTORS,
                        CommonAttributes.HA,
                        ConnectionFactoryAttributes.Common.CLIENT_FAILURE_CHECK_PERIOD,
                        ConnectionFactoryAttributes.Common.CONNECTION_TTL,
                        CommonAttributes.CALL_TIMEOUT,
                        CommonAttributes.CALL_FAILOVER_TIMEOUT,
                        ConnectionFactoryAttributes.Common.CONSUMER_WINDOW_SIZE,
                        ConnectionFactoryAttributes.Common.CONSUMER_MAX_RATE,
                        ConnectionFactoryAttributes.Common.CONFIRMATION_WINDOW_SIZE,
                        ConnectionFactoryAttributes.Common.PRODUCER_WINDOW_SIZE,
                        ConnectionFactoryAttributes.Common.PRODUCER_MAX_RATE,
                        ConnectionFactoryAttributes.Common.PROTOCOL_MANAGER_FACTORY,
                        ConnectionFactoryAttributes.Common.COMPRESS_LARGE_MESSAGES,
                        ConnectionFactoryAttributes.Common.CACHE_LARGE_MESSAGE_CLIENT,
                        CommonAttributes.MIN_LARGE_MESSAGE_SIZE,
                        CommonAttributes.CLIENT_ID,
                        ConnectionFactoryAttributes.Common.DUPS_OK_BATCH_SIZE,
                        ConnectionFactoryAttributes.Common.TRANSACTION_BATCH_SIZE,
                        ConnectionFactoryAttributes.Common.BLOCK_ON_ACKNOWLEDGE,
                        ConnectionFactoryAttributes.Common.BLOCK_ON_NON_DURABLE_SEND,
                        ConnectionFactoryAttributes.Common.BLOCK_ON_DURABLE_SEND,
                        ConnectionFactoryAttributes.Common.AUTO_GROUP,
                        ConnectionFactoryAttributes.Common.PRE_ACKNOWLEDGE,
                        ConnectionFactoryAttributes.Common.RETRY_INTERVAL,
                        ConnectionFactoryAttributes.Common.RETRY_INTERVAL_MULTIPLIER,
                        CommonAttributes.MAX_RETRY_INTERVAL,
                        ConnectionFactoryAttributes.Common.RECONNECT_ATTEMPTS,
                        ConnectionFactoryAttributes.Common.FAILOVER_ON_INITIAL_CONNECTION,
                        ConnectionFactoryAttributes.Common.CONNECTION_LOAD_BALANCING_CLASS_NAME,
                        ConnectionFactoryAttributes.Common.USE_GLOBAL_POOLS,
                        ConnectionFactoryAttributes.Common.SCHEDULED_THREAD_POOL_MAX_SIZE,
                        ConnectionFactoryAttributes.Common.THREAD_POOL_MAX_SIZE,
                        ConnectionFactoryAttributes.Common.GROUP_ID,
                        ConnectionFactoryAttributes.Common.DESERIALIZATION_BLACKLIST,
                        ConnectionFactoryAttributes.Common.DESERIALIZATION_WHITELIST,
                        ConnectionFactoryAttributes.Common.USE_TOPOLOGY,
                        // pooled
                        // inbound config
                        ConnectionFactoryAttributes.Pooled.USE_JNDI,
                        ConnectionFactoryAttributes.Pooled.JNDI_PARAMS,
                        ConnectionFactoryAttributes.Pooled.REBALANCE_CONNECTIONS,
                        ConnectionFactoryAttributes.Pooled.USE_LOCAL_TX,
                        ConnectionFactoryAttributes.Pooled.SETUP_ATTEMPTS,
                        ConnectionFactoryAttributes.Pooled.SETUP_INTERVAL,
                        // outbound config
                        ConnectionFactoryAttributes.Pooled.ALLOW_LOCAL_TRANSACTIONS,
                        ConnectionFactoryAttributes.Pooled.TRANSACTION,
                        ConnectionFactoryAttributes.Pooled.USER,
                        ConnectionFactoryAttributes.Pooled.PASSWORD,
                        ConnectionFactoryAttributes.Pooled.CREDENTIAL_REFERENCE,
                        ConnectionFactoryAttributes.Pooled.MIN_POOL_SIZE,
                        ConnectionFactoryAttributes.Pooled.USE_AUTO_RECOVERY,
                        ConnectionFactoryAttributes.Pooled.MAX_POOL_SIZE,
                        ConnectionFactoryAttributes.Pooled.MANAGED_CONNECTION_POOL,
                        ConnectionFactoryAttributes.Pooled.ENLISTMENT_TRACE,
                        ConnectionFactoryAttributes.Common.INITIAL_MESSAGE_PACKET_SIZE,
                        ConnectionFactoryAttributes.Pooled.INITIAL_CONNECT_ATTEMPTS,
                        ConnectionFactoryAttributes.Pooled.STATISTICS_ENABLED);
        if (external) {
            builder.addAttributes(ConnectionFactoryAttributes.External.ENABLE_AMQ1_PREFIX);
        }
        return builder;
    }

}
