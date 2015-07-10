package org.keycloak.testsuite.adapter.tomcat;

import org.junit.Ignore;
import org.keycloak.testsuite.adapter.AbstractBasicAuthExampleAdapterTest;
import org.keycloak.testsuite.arquillian.annotation.AppServerContainer;

/**
 *
 * @author tkyjovsk
 */
@AppServerContainer("app-server-tomcat")
@Ignore
public class TomcatBasicAuthExampleAdapterTest extends AbstractBasicAuthExampleAdapterTest {

    // TODO find out how to add context.xml dependent on app context (web.xml/module-name)
}
