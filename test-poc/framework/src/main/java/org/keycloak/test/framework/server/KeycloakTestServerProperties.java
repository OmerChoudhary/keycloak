package org.keycloak.test.framework.server;

public class KeycloakTestServerProperties {

    public static final String KEYCLOAK_TEST_SERVER_ENV_KEY = "keycloak-server";

    public static String KEYCLOAK_TEST_SERVER_ENV_VALUE = System.getProperty(KEYCLOAK_TEST_SERVER_ENV_KEY) == null
            || System.getProperty(KEYCLOAK_TEST_SERVER_ENV_KEY).isEmpty()
            ? "embedded" : System.getProperty(KEYCLOAK_TEST_SERVER_ENV_KEY);

}
