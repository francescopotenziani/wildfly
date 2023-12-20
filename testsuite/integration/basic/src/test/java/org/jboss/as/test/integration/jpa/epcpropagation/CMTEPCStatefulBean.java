/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.jpa.epcpropagation;

import java.util.Locale;
import jakarta.annotation.Resource;
import jakarta.ejb.EJB;
import jakarta.ejb.Local;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateful;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceContextType;

/**
 * @author <a href="mailto:bdecoste@jboss.com">William DeCoste</a>
 */
@Stateful
@Local(StatefulInterface.class)
public class CMTEPCStatefulBean extends AbstractStatefulInterface {
    @PersistenceContext(type = PersistenceContextType.EXTENDED, unitName = "mypc")
    EntityManager em;

    @Resource
    SessionContext sessionContext;

    @EJB
    StatelessInterface cmtBean;

    public boolean execute(Integer id, String name) throws Exception {
        MyEntity eb = em.find(MyEntity.class, id);
        eb.setName(name.toUpperCase(Locale.ENGLISH));

        String propagatedName = cmtBean.updateEntity(id, name.toLowerCase(Locale.ENGLISH));

        return propagatedName.equals(name.toUpperCase(Locale.ENGLISH));
    }
}
