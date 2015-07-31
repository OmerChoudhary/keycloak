package org.keycloak.testsuite.console.clients;

import org.jboss.arquillian.graphene.findby.FindByJQuery;
import org.jboss.arquillian.graphene.page.Page;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.testsuite.console.AbstractAdminConsoleTest;
import org.keycloak.testsuite.console.page.fragment.FlashMessage;
import org.keycloak.testsuite.console.page.fragment.RoleMappings;
import org.keycloak.testsuite.console.page.clients.Clients;
import org.keycloak.testsuite.console.page.roles.RolesPage;
import org.keycloak.testsuite.console.page.users.Users;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import org.keycloak.representations.idm.ClientRepresentation;
import static org.keycloak.representations.idm.CredentialRepresentation.PASSWORD;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testsuite.arquillian.jira.Jira;
import static org.openqa.selenium.By.linkText;

/**
 * Created by fkiss.
 */
public class AddClientRoleTest extends AbstractAdminConsoleTest {

    @Page
    private Clients page;

    @Page
    private Users userPage;

    @Page
    private RolesPage rolesPage;

    @Page
    private RoleMappings roleMappings;

    @FindByJQuery(".alert")
    private FlashMessage flashMessage;

    @Before
    public void beforeTestAddClientRole() {
        navigation.clients();
    }

    private ClientRepresentation createClient(String name, String redirectUri) {
        ClientRepresentation client = new ClientRepresentation();
        client.setName(name);
        List<String> redirectUris = new ArrayList<>();
        redirectUris.add(redirectUri);
        client.setRedirectUris(redirectUris);
        return client;
    }

    @Test
    public void testAddClientRole() {
        ClientRepresentation newClient = createClient("test-client1", "http://example.com/*");
        RoleRepresentation newRole = new RoleRepresentation("client-role", "");

        page.addClient(newClient);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        navigation.rolesTab(newClient.getName());
        rolesPage.addRole(newRole);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        navigation.clients();
        page.findClient(newClient.getName());
        page.goToClient(newClient);
        navigation.rolesTab(newClient.getName());
        assertNotNull(driver.findElement(linkText(newRole.getName())));

        navigation.clients();
        page.deleteClient(newClient.getName());
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        assertNull(page.findClient(newClient.getName()));
    }

    @Test
    @Jira("KEYCLOAK-1497")
    public void testAddClientRoleToUser() {
        ClientRepresentation newClient = createClient("test-client2", "http://example.com/*");
        RoleRepresentation newRole = new RoleRepresentation("client-role2", "");
        String testUsername = "test-user2";
        UserRepresentation newUser = new UserRepresentation();
        newUser.setUsername(testUsername);
        newUser.credential(PASSWORD, "pass");

        page.addClient(newClient);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        navigation.rolesTab(newClient.getName());
        rolesPage.addRole(newRole);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        navigation.clients();
        page.findClient(newClient.getName());
        page.goToClient(newClient);
        navigation.rolesTab(newClient.getName());
        assertNotNull(driver.findElement(linkText(newRole.getName())));

        navigation.users();
        userPage.addUser(newUser);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        navigation.users();
        userPage.findUser(testUsername);
        userPage.goToUser(testUsername);

        navigation.roleMappings(testUsername);
        roleMappings.selectClientRole(newClient.getName());
        roleMappings.addAvailableClientRole(newRole.getName());
        //flashMessage.waitUntilPresent();
        //assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        //KEYCLOAK-1497
        assertTrue(roleMappings.isAssignedClientRole(newRole.getName()));

        navigation.users();
        userPage.deleteUser(testUsername);

        navigation.clients();
        page.deleteClient(newClient.getName());
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        assertNull(page.findClient(newClient.getName()));
    }

    @Test
    @Jira("KEYCLOAK-1496, KEYCLOAK-1497")
    public void testAddCompositeRealmClientRoleToUser() {
        ClientRepresentation newClient = createClient("test-client3", "http://example.com/*");
        RoleRepresentation clientCompositeRole = new RoleRepresentation("client-composite-role", "");
        String testUsername = "test-user3";
        UserRepresentation newUser = new UserRepresentation();
        newUser.setUsername(testUsername);
        newUser.credential(PASSWORD, "pass");

        RoleRepresentation subRole1 = new RoleRepresentation("sub-role1", "");
        RoleRepresentation subRole2 = new RoleRepresentation("sub-role2", "");
        List<RoleRepresentation> roles = new ArrayList<>();
        clientCompositeRole.setComposite(true);
        roles.add(subRole1);
        roles.add(subRole2);

        //create sub-roles
        navigation.roles();
        for (RoleRepresentation role : roles) {
            rolesPage.addRole(role);
            flashMessage.waitUntilPresent();
            assertTrue(flashMessage.getText(), flashMessage.isSuccess());
            navigation.roles();
            assertEquals(role.getName(), rolesPage.findRole(role.getName()).getName());
        }

        //create client
        navigation.clients();
        page.addClient(newClient);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        //add client role
        navigation.rolesTab(newClient.getName());
        rolesPage.addRole(clientCompositeRole);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        //add realm composite roles
        rolesPage.setCompositeRole(clientCompositeRole);
        roleMappings.addAvailableRole(subRole1.getName(), subRole2.getName());
        //flashMessage.waitUntilPresent();
        //assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        //KEYCLOAK-1497

        //create user
        navigation.users();
        userPage.addUser(newUser);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        //add client role to user and verify
        navigation.users();
        userPage.findUser(testUsername);
        userPage.goToUser(testUsername);

        navigation.roleMappings(testUsername);
        roleMappings.selectClientRole(newClient.getName());
        roleMappings.addAvailableClientRole(clientCompositeRole.getName());
        //flashMessage.waitUntilPresent();
        //assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        //KEYCLOAK-1497
        assertTrue(roleMappings.isAssignedClientRole(clientCompositeRole.getName()));
        assertTrue(roleMappings.checkIfEffectiveRealmRolesAreComplete(subRole1, subRole2)); //KEYCLOAK-1496
        assertTrue(roleMappings.checkIfEffectiveClientRolesAreComplete(clientCompositeRole));

        //delete everything
        navigation.users();
        userPage.deleteUser(testUsername);

        navigation.roles();
        rolesPage.deleteRole(subRole1);
        navigation.roles();
        rolesPage.deleteRole(subRole2);

        navigation.clients();
        page.deleteClient(newClient.getName());
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        assertNull(page.findClient(newClient.getName()));
    }

    @Test
    @Jira("KEYCLOAK-1504, KEYCLOAK-1497")
    public void testAddCompositeClientRoleToUser() {
        ClientRepresentation newClient = createClient("test-client4", "http://example.com/*");
        RoleRepresentation clientCompositeRole = new RoleRepresentation("client-composite-role2", "");
        String testUsername = "test-user4";
        UserRepresentation newUser = new UserRepresentation();
        newUser.setUsername(testUsername);
        newUser.credential(PASSWORD, "pass");

        RoleRepresentation subRole1 = new RoleRepresentation("client-sub-role1", "");
        RoleRepresentation subRole2 = new RoleRepresentation("client-sub-role2", "");
        List<RoleRepresentation> roles = new ArrayList<>();
        clientCompositeRole.setComposite(true);
        roles.add(clientCompositeRole);
        roles.add(subRole1);
        roles.add(subRole2);

        //create client
        page.addClient(newClient);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        //create sub-roles
        navigation.rolesTab(newClient.getName());
        for (RoleRepresentation role : roles) {
            navigation.clients();
            page.goToClient(newClient);
            navigation.rolesTab(newClient.getName());
            rolesPage.addRole(role);
            flashMessage.waitUntilPresent();
            assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        }

        //add client composite roles
        navigation.clients();
        page.goToClient(newClient);
        navigation.rolesTab(newClient.getName());
        rolesPage.goToRole(clientCompositeRole);
        rolesPage.setCompositeRole(clientCompositeRole);
        roleMappings.selectClientRole(newClient.getName());
        roleMappings.addAvailableClientRole(subRole1.getName(), subRole2.getName());
        //flashMessage.waitUntilPresent();
        //assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        //KEYCLOAK-1504, KEYCLOAK-1497

        //create user
        navigation.users();
        userPage.addUser(newUser);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        //add client role to user and verify
        navigation.users();
        userPage.findUser(testUsername);
        userPage.goToUser(testUsername);

        navigation.roleMappings(testUsername);
        roleMappings.selectClientRole(newClient.getName());
        roleMappings.addAvailableClientRole(clientCompositeRole.getName());
        assertTrue(roleMappings.isAssignedClientRole(clientCompositeRole.getName()));
        assertTrue(roleMappings.checkIfEffectiveClientRolesAreComplete(clientCompositeRole, subRole1, subRole2));

        //delete everything
        navigation.users();
        userPage.deleteUser(testUsername);

        navigation.roles();
        rolesPage.deleteRole(subRole1);
        navigation.roles();
        rolesPage.deleteRole(subRole2);

        navigation.clients();
        page.deleteClient(newClient.getName());
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        assertNull(page.findClient(newClient.getName()));
    }
}
