/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ejb.transaction.descriptor;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Tests for override transaction type for method through ejb-jar.xml.
 * Bugzilla 1180556 https://bugzilla.redhat.com/show_bug.cgi?id=1180556
 * @author Hynek Svabek
 *
 */
@RunWith(Arquillian.class)
public class EjbTransactionTypeOverrideTestCase {

    @ArquillianResource
    private InitialContext initialContext;

    @Deployment
    public static Archive<?> deploy() {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "test-tx-override-descriptor.jar");
        jar.addPackage(EjbTransactionDescriptorTestCase.class.getPackage());
        jar.addAsManifestResource(EjbTransactionDescriptorTestCase.class.getPackage(), "ejb-jar-override.xml", "ejb-jar.xml");
        return jar;
    }

    @Test
    public void testOverrideSupportToRequiredThroughtEjbJarXml() throws SystemException, NotSupportedException, NamingException {
        final TransactionLocal bean = (TransactionLocal) initialContext.lookup("java:module/" + DescriptorBean.class.getSimpleName() + "!" + TransactionLocal.class.getName());
        Assert.assertEquals(Status.STATUS_NO_TRANSACTION, bean.transactionStatus());
        Assert.assertEquals(Status.STATUS_ACTIVE, bean.transactionStatus2());
    }
}
