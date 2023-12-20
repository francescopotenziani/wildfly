/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.cluster.xsite;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.clustering.cluster.AbstractClusteringTestCase;
import org.jboss.as.test.http.util.TestHttpClientUtils;
import org.jboss.as.test.shared.ManagementServerSetupTask;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test xsite functionality on a 4-node, 3-site test deployment:
 *
 * sites:
 *   LON: {LON-0, LON-1}  // maps to NODE_1/NODE_2
 *   NYC: {NYC-0}         // maps to NODE_3
 *   SFO: {SFO-0}         // maps to NODE_4
 *
 * routes:
 *   LON -> NYC,SFO
 *   NYC -> LON
 *   SFO -> LON
 *
 * backups: (<site>:<container>:<cache>)
 *   LON:web:dist backed up by NYC:web:dist, SFO:web:dist
 *   NYC not backed up
 *   SFO not backed up
 *
 * @author Richard Achmatowicz
 */
@RunWith(Arquillian.class)
@ServerSetup({ XSiteSimpleTestCase.CacheSetupTask.class, XSiteSimpleTestCase.ServerSetupTask.class })
public class XSiteSimpleTestCase extends AbstractClusteringTestCase {

    private static final String MODULE_NAME = XSiteSimpleTestCase.class.getSimpleName();

    public XSiteSimpleTestCase() {
        super(NODE_1_2_3_4);
    }

    @Deployment(name = DEPLOYMENT_1, managed = false, testable = false)
    @TargetsContainer(NODE_1)
    public static Archive<?> deployment1() {
        return getDeployment();
    }

    @Deployment(name = DEPLOYMENT_2, managed = false, testable = false)
    @TargetsContainer(NODE_2)
    public static Archive<?> deployment2() {
        return getDeployment();
    }

    @Deployment(name = DEPLOYMENT_3, managed = false, testable = false)
    @TargetsContainer(NODE_3)
    public static Archive<?> deployment3() {
        return getDeployment();
    }

    @Deployment(name = DEPLOYMENT_4, managed = false, testable = false)
    @TargetsContainer(NODE_4)
    public static Archive<?> deployment4() {
        return getDeployment();
    }

    private static Archive<?> getDeployment() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, MODULE_NAME + ".war");
        war.addClass(CacheAccessServlet.class);
        war.setWebXML(XSiteSimpleTestCase.class.getPackage(), "web.xml");
        war.setManifest(new StringAsset("Manifest-Version: 1.0\nDependencies: org.infinispan\n"));
        return war;
    }

    @Override
    public void beforeTestMethod() throws Exception {
        // Orchestrate startup of clusters to purge previously discovered views.
        stop();
        start(NODE_1);
        deploy(DEPLOYMENT_1);
        start(NODE_3);
        deploy(DEPLOYMENT_3);
        start(NODE_4);
        deploy(DEPLOYMENT_4);
        start(NODE_2);
        deploy(DEPLOYMENT_2);
    }

    @Test
    public void test(@ArquillianResource(CacheAccessServlet.class) @OperateOnDeployment(DEPLOYMENT_1) URL baseURL1,
                     @ArquillianResource(CacheAccessServlet.class) @OperateOnDeployment(DEPLOYMENT_2) URL baseURL2,
                     @ArquillianResource(CacheAccessServlet.class) @OperateOnDeployment(DEPLOYMENT_3) URL baseURL3,
                     @ArquillianResource(CacheAccessServlet.class) @OperateOnDeployment(DEPLOYMENT_4) URL baseURL4
    ) throws IllegalStateException, IOException, URISyntaxException, InterruptedException {
        /*
         * Tests that puts get relayed to their backup sites
         *
         * Put the key-value (a,100) on LON-0 on site LON and check that the key-value pair:
         *   arrives at LON-1 on site LON
         *   arrives at NYC-0 on site NYC
         *   arrives at SFO-0 on site SFO
         */

        String value = "100";
        URI url1 = CacheAccessServlet.createPutURI(baseURL1, "a", value);
        URI url2 = CacheAccessServlet.createGetURI(baseURL2, "a");
        URI url3 = CacheAccessServlet.createGetURI(baseURL3, "a");
        URI url4 = CacheAccessServlet.createGetURI(baseURL4, "a");

        try (CloseableHttpClient client = TestHttpClientUtils.promiscuousCookieHttpClient()) {
            // put a value to LON-0
            HttpResponse response = client.execute(new HttpGet(url1));
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            response.getEntity().getContent().close();

            // Lets wait for the session to replicate
            Thread.sleep(GRACE_TIME_TO_REPLICATE);

            // do a get on LON-1
            response = client.execute(new HttpGet(url2));
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            Assert.assertEquals(value, response.getFirstHeader("value").getValue());
            response.getEntity().getContent().close();

            // do a get on NYC-0
            response = client.execute(new HttpGet(url3));
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            Assert.assertEquals(value, response.getFirstHeader("value").getValue());
            response.getEntity().getContent().close();

            // do a get on SFO-0
            response = client.execute(new HttpGet(url4));
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            Assert.assertEquals(value, response.getFirstHeader("value").getValue());
            response.getEntity().getContent().close();
        }

        /*
         * Tests that puts at the backup caches do not get relayed back to the origin cache.
         *
         * Put the key-value (b,200) on NYC-0 on site NYC and check that the key-value pair:
         *   does not arrive at LON-0 on site LON
         */

        url1 = CacheAccessServlet.createGetURI(baseURL1, "b");
        url3 = CacheAccessServlet.createPutURI(baseURL3, "b", "200");

        try (CloseableHttpClient client = TestHttpClientUtils.promiscuousCookieHttpClient()) {
            // put a value to NYC-0
            HttpResponse response = client.execute(new HttpGet(url3));
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            response.getEntity().getContent().close();

            // Lets wait for the session to replicate
            Thread.sleep(GRACE_TIME_TO_REPLICATE);

            // do a get on LON-1 - this should fail
            response = client.execute(new HttpGet(url1));
            Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.getStatusLine().getStatusCode());
            response.getEntity().getContent().close();
        }
    }

    public static class CacheSetupTask extends ManagementServerSetupTask {
        public CacheSetupTask() {
            super(NODE_1_2_3_4, createContainerConfigurationBuilder()
                    .setupScript(createScriptBuilder()
                            .startBatch()
                                .add("/subsystem=infinispan/cache-container=foo:add(marshaller=JBOSS)")
                                .add("/subsystem=infinispan/cache-container=foo/transport=jgroups:add")
                                .add("/subsystem=infinispan/cache-container=foo/distributed-cache=bar:add")
                            .endBatch()
                            .build())
                    .tearDownScript(createScriptBuilder()
                            .add("/subsystem=infinispan/cache-container=foo:remove")
                            .build())
                    .build());
        }
    }

    public static class ServerSetupTask extends ManagementServerSetupTask {
        public ServerSetupTask() {
            super(createContainerSetConfigurationBuilder()
                    // LON
                    .addContainers(NODE_1_2, createContainerConfigurationBuilder()
                        .setupScript(createScriptBuilder()
                            .startBatch()
                                .add("/subsystem=infinispan/cache-container=foo/distributed-cache=bar/component=backups/backup=NYC:add(failure-policy=WARN, strategy=SYNC, timeout=10000, enabled=true)")
                                .add("/subsystem=infinispan/cache-container=foo/distributed-cache=bar/component=backups/backup=SFO:add(failure-policy=WARN, strategy=SYNC, timeout=10000, enabled=true)")
                                .add("/subsystem=jgroups/channel=bridge:add(stack=tcp-bridge)")
                                .add("/subsystem=jgroups/stack=tcp/relay=relay.RELAY2:add(site=LON)")
                                .add("/subsystem=jgroups/stack=tcp/relay=relay.RELAY2/remote-site=NYC:add(channel=bridge)")
                                .add("/subsystem=jgroups/stack=tcp/relay=relay.RELAY2/remote-site=SFO:add(channel=bridge)")
                                .add("/subsystem=jgroups/stack=tcp/protocol=TCPPING:write-attribute(name=socket-bindings, value=[node-1, node-2])")
                            .endBatch()
                            .build())
                        .tearDownScript(createScriptBuilder()
                            .startBatch()
                                .add("/subsystem=jgroups/stack=tcp/protocol=TCPPING:write-attribute(name=socket-bindings, value=[node-1, node-2, node-3, node-4])")
                                .add("/subsystem=jgroups/stack=tcp/relay=relay.RELAY2:remove")
                                .add("/subsystem=jgroups/channel=bridge:remove")
                                .add("/subsystem=infinispan/cache-container=foo/distributed-cache=bar/component=backups/backup=SFO:remove")
                                .add("/subsystem=infinispan/cache-container=foo/distributed-cache=bar/component=backups/backup=NYC:remove")
                            .endBatch()
                            .build())
                        .build())
                    // NYC
                    .addContainer(NODE_3, createContainerConfigurationBuilder()
                        .setupScript(createScriptBuilder()
                            .startBatch()
                                .add("/subsystem=jgroups/channel=bridge:add(stack=tcp-bridge)")
                                .add("/subsystem=jgroups/stack=tcp/relay=relay.RELAY2:add(site=NYC)")
                                .add("/subsystem=jgroups/stack=tcp/relay=relay.RELAY2/remote-site=LON:add(channel=bridge)")
                                .add("/subsystem=jgroups/stack=tcp/relay=relay.RELAY2/remote-site=SFO:add(channel=bridge)")
                                .add("/subsystem=jgroups/stack=tcp/protocol=TCPPING:write-attribute(name=socket-bindings, value=[node-3])")
                            .endBatch()
                            .build())
                        .tearDownScript(createScriptBuilder()
                            .startBatch()
                                .add("/subsystem=jgroups/stack=tcp/protocol=TCPPING:write-attribute(name=socket-bindings, value=[node-1, node-2, node-3, node-4])")
                                .add("/subsystem=jgroups/stack=tcp/relay=relay.RELAY2:remove")
                                .add("/subsystem=jgroups/channel=bridge:remove")
                            .endBatch()
                            .build())
                        .build())
                    // SFO
                    .addContainer(NODE_4, createContainerConfigurationBuilder()
                        .setupScript(createScriptBuilder()
                            .startBatch()
                                .add("/subsystem=jgroups/channel=bridge:add(stack=tcp-bridge)")
                                .add("/subsystem=jgroups/stack=tcp/relay=relay.RELAY2:add(site=SFO)")
                                .add("/subsystem=jgroups/stack=tcp/relay=relay.RELAY2/remote-site=LON:add(channel=bridge)")
                                .add("/subsystem=jgroups/stack=tcp/relay=relay.RELAY2/remote-site=NYC:add(channel=bridge)")
                                .add("/subsystem=jgroups/stack=tcp/protocol=TCPPING:write-attribute(name=socket-bindings, value=[node-4])")
                            .endBatch()
                            .build())
                        .tearDownScript(createScriptBuilder()
                            .startBatch()
                                .add("/subsystem=jgroups/stack=tcp/protocol=TCPPING:write-attribute(name=socket-bindings, value=[node-1, node-2, node-3, node-4])")
                                .add("/subsystem=jgroups/stack=tcp/relay=relay.RELAY2:remove")
                                .add("/subsystem=jgroups/channel=bridge:remove")
                            .endBatch()
                            .build())
                        .build())
                    .build());
        }
    }
}