package org.keycloak.testsuite.console.page.clients;

import org.jboss.arquillian.graphene.page.Page;
import org.keycloak.testsuite.console.page.AdminConsoleCreate;

/**
 *
 * @author tkyjovsk
 */
public class CreateClient extends AdminConsoleCreate {

    public CreateClient() {
        setEntity("client");
    }
    
    @Page
    private ClientForm form;
    
    public ClientForm form() {
        return form;
    }
    
}
