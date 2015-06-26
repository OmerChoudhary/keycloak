package org.keycloak.testsuite.adapter.example;

import org.keycloak.testsuite.arquillian.annotation.AppServerContainer;
import org.keycloak.testsuite.arquillian.annotation.AuthServerContainer;

/**
 *
 * @author tkyjovsk
 */
@AuthServerContainer("auth-server-wildfly")
@AppServerContainer("app-server-wildfly")
public class WildflyExamplesAdapterTest extends AbstractExamplesAdapterTest {

}
