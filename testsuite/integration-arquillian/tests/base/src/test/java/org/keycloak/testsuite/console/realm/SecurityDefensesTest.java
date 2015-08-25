/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.testsuite.console.realm;

import org.jboss.arquillian.graphene.page.Page;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.keycloak.testsuite.console.page.realm.SecurityDefenses;

/**
 *
 * @author Filip Kiss
 */
public class SecurityDefensesTest extends AbstractRealmTest {

    @Page
    private SecurityDefenses securityDefenses;

    @Before
    public void beforeSecuritySettingsTest() {
        configure().realmSettings();
        tabs().securityDefenses();
    }

    @Test
    @Ignore // TODO
    public void securitySettingsTest() {
        //TODO:
        securityDefenses.goToBruteForceDetection();
        securityDefenses.bruteForceDetection().form().setMaxLoginFailures("5");
    }
}
