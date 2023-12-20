/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ee.injection.support.websocket;

import java.net.SocketPermission;
import java.net.URI;
import java.util.PropertyPermission;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.test.integration.ee.injection.support.Alpha;
import org.jboss.as.test.integration.ee.injection.support.Bravo;
import org.jboss.as.test.integration.ee.injection.support.ComponentInterceptor;
import org.jboss.as.test.integration.ee.injection.support.ComponentInterceptorBinding;
import org.jboss.as.test.integration.ee.injection.support.InjectionSupportTestCase;
import org.jboss.as.test.shared.AssumeTestGroupUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.jboss.as.test.shared.PermissionUtils.createPermissionsXmlAsset;

/**
 * @author Matus Abaffy
 */
@RunWith(Arquillian.class)
public class WebSocketInjectionSupportTestCase {

    @BeforeClass
    public static void beforeClass() {
        // TODO WFLY-16551
        AssumeTestGroupUtil.assumeSecurityManagerDisabled();
    }

    @Deployment
    public static WebArchive deploy() {
        return ShrinkWrap
                .create(WebArchive.class, "websocket.war")
                .addPackage(WebSocketInjectionSupportTestCase.class.getPackage())
                .addClasses(TestSuiteEnvironment.class, Alpha.class, Bravo.class, ComponentInterceptorBinding.class,
                        ComponentInterceptor.class).addClasses(InjectionSupportTestCase.constructTestsHelperClasses)
                .addAsWebInfResource(new StringAsset("<beans bean-discovery-mode=\"all\"></beans>"), "beans.xml")
                .addAsManifestResource(new StringAsset("io.undertow.websockets.jsr.UndertowContainerProvider"),
                        "services/jakarta.websocket.ContainerProvider")
                .addAsManifestResource(createPermissionsXmlAsset(
                        // Needed for the TestSuiteEnvironment.getServerAddress() and TestSuiteEnvironment.getHttpPort()
                        new PropertyPermission("management.address", "read"),
                        new PropertyPermission("node0", "read"),
                        new PropertyPermission("jboss.http.port", "read"),
                        // Needed for the serverContainer.connectToServer()
                        new SocketPermission("*:" + TestSuiteEnvironment.getHttpPort(), "connect,resolve"),
                        // Needed for xnio's WorkerThread which binds to Xnio.ANY_INET_ADDRESS, see WFLY-7538
                        new SocketPermission("*:0", "listen,resolve")),
                        "permissions.xml");
    }

    @Test
    public void testWebSocketInjectionAndInterception() throws Exception {
        AnnotatedClient.reset();
        AnnotatedEndpoint.reset();
        ComponentInterceptor.resetInterceptions();

        final WebSocketContainer serverContainer = ContainerProvider.getWebSocketContainer();
        serverContainer.connectToServer(AnnotatedClient.class, new URI("ws", "", TestSuiteEnvironment.getServerAddress(), TestSuiteEnvironment.getHttpPort(), "/websocket/websocket/cruel", "", ""));

        Assert.assertEquals("Hello cruel World", AnnotatedClient.getMessage());

        Assert.assertTrue("Client endpoint's injection not correct.", AnnotatedClient.injectionOK);
        Assert.assertTrue("Server endpoint's injection not correct.", AnnotatedEndpoint.injectionOK);

        Assert.assertTrue("PostConstruct method on client endpoint instance not called.", AnnotatedClient.postConstructCalled);
        Assert.assertTrue("PostConstruct method on server endpoint instance not called.", AnnotatedEndpoint.postConstructCalled);

        Assert.assertEquals("AroundConstruct interceptor method not invoked for client endpoint.",
                "AroundConstructInterceptor#Joe#AnnotatedClient", AnnotatedClient.getName());
        Assert.assertEquals("AroundConstruct interceptor method not invoked for server endpoint.",
                "AroundConstructInterceptor#Joe#AnnotatedEndpoint", AnnotatedEndpoint.getName());

        Assert.assertEquals(2, ComponentInterceptor.getInterceptions().size());
        Assert.assertEquals("open", ComponentInterceptor.getInterceptions().get(0).getMethodName());
        Assert.assertEquals("message", ComponentInterceptor.getInterceptions().get(1).getMethodName());
    }
}
