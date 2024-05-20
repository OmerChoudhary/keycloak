package org.keycloak.test.framework.server;

import static org.keycloak.test.framework.server.KeycloakTestServerType.*;
import static org.keycloak.test.framework.server.KeycloakTestServerProperties.KEYCLOAK_TEST_SERVER_ENV_VALUE;

public class KeycloakTestServerProducer {

    public static KeycloakTestServer createKeycloakTestServerInstance(KeycloakTestServerType keycloakTestServerType) {
        switch (keycloakTestServerType) {
            case STANDALONE: return new StandaloneKeycloakTestServer();
            case REMOTE: return new RemoteKeycloakTestServer();
            default: return new EmbeddedKeycloakTestServer();
        }
    }

    public static KeycloakTestServer createKeycloakTestServerInstance() {
        return createKeycloakTestServerInstance(getKeycloakTestServerType());
    }

    public static KeycloakTestServerType getKeycloakTestServerType() {
        switch (KEYCLOAK_TEST_SERVER_ENV_VALUE) {
            case "standalone": return STANDALONE;
            case "remote": return REMOTE;
            default: return EMBEDDED;
        }
    }

}
