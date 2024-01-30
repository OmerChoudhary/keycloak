/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.testsuite.authz;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.hamcrest.Matchers;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.resource.AuthorizationResource;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.ResourceResource;
import org.keycloak.admin.client.resource.ScopePermissionsResource;
import org.keycloak.authorization.client.AuthorizationDeniedException;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.authorization.client.representation.TokenIntrospectionResponse;
import org.keycloak.authorization.client.util.HttpResponseException;
import org.keycloak.common.util.Base64Url;
import org.keycloak.events.EventType;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.mappers.HardcodedClaim;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessToken.Authorization;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmEventsConfigRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.keycloak.representations.idm.authorization.AuthorizationRequest.Metadata;
import org.keycloak.representations.idm.authorization.AuthorizationResponse;
import org.keycloak.representations.idm.authorization.DecisionStrategy;
import org.keycloak.representations.idm.authorization.JSPolicyRepresentation;
import org.keycloak.representations.idm.authorization.Permission;
import org.keycloak.representations.idm.authorization.PermissionRequest;
import org.keycloak.representations.idm.authorization.PermissionResponse;
import org.keycloak.representations.idm.authorization.PermissionTicketRepresentation;
import org.keycloak.representations.idm.authorization.ResourcePermissionRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.keycloak.representations.idm.authorization.ResourceServerRepresentation;
import org.keycloak.representations.idm.authorization.ScopePermissionRepresentation;
import org.keycloak.representations.idm.authorization.ScopeRepresentation;
import org.keycloak.representations.idm.authorization.UserPolicyRepresentation;
import org.keycloak.testsuite.AssertEvents;
import org.keycloak.testsuite.admin.ApiUtil;
import org.keycloak.testsuite.client.resources.TestApplicationResourceUrls;
import org.keycloak.testsuite.util.ClientBuilder;
import org.keycloak.testsuite.util.OAuthClient;
import org.keycloak.testsuite.util.RealmBuilder;
import org.keycloak.testsuite.util.RoleBuilder;
import org.keycloak.testsuite.util.RolesBuilder;
import org.keycloak.testsuite.util.UserBuilder;
import org.keycloak.util.JsonSerialization;

/**
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public class EntitlementAPITest extends AbstractAuthzTest {

    private static final String RESOURCE_SERVER_TEST = "resource-server-test";
    private static final String TEST_CLIENT = "test-client";
    private static final String AUTHZ_CLIENT_CONFIG = "default-keycloak.json";
    private static final String PAIRWISE_RESOURCE_SERVER_TEST = "pairwise-resource-server-test";
    private static final String PAIRWISE_TEST_CLIENT = "test-client-pairwise";
    private static final String PAIRWISE_AUTHZ_CLIENT_CONFIG = "default-keycloak-pairwise.json";
    private static final String PUBLIC_TEST_CLIENT = "test-public-client";
    private static final String PUBLIC_TEST_CLIENT_CONFIG = "default-keycloak-public-client.json";

    private AuthzClient authzClient;

    @ArquillianResource
    protected ContainerController controller;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public AssertEvents events = new AssertEvents(this);

    @Override
    public void addTestRealms(List<RealmRepresentation> testRealms) {
        testRealms.add(RealmBuilder.create().name("authz-test")
                .roles(RolesBuilder.create().realmRole(RoleBuilder.create().name("uma_authorization").build()))
                .user(UserBuilder.create().username("marta").password("password").addRoles("uma_authorization"))
                .user(UserBuilder.create().username("kolo").password("password"))
                .user(UserBuilder.create().username("offlineuser").password("password").addRoles("offline_access"))
                .client(ClientBuilder.create().clientId(RESOURCE_SERVER_TEST)
                        .secret("secret")
                        .authorizationServicesEnabled(true)
                        .redirectUris("http://localhost/resource-server-test")
                        .defaultRoles("uma_protection")
                        .directAccessGrants())
                .client(ClientBuilder.create().clientId(PAIRWISE_RESOURCE_SERVER_TEST)
                        .secret("secret")
                        .authorizationServicesEnabled(true)
                        .redirectUris("http://localhost/resource-server-test")
                        .defaultRoles("uma_protection")
                        .pairwise(TestApplicationResourceUrls.pairwiseSectorIdentifierUri())
                        .directAccessGrants())
                .client(ClientBuilder.create().clientId(TEST_CLIENT)
                        .secret("secret")
                        .authorizationServicesEnabled(true)
                        .redirectUris("http://localhost/test-client")
                        .directAccessGrants())
                .client(ClientBuilder.create().clientId(PAIRWISE_TEST_CLIENT)
                        .secret("secret")
                        .authorizationServicesEnabled(true)
                        .redirectUris("http://localhost/test-client")
                        .pairwise(TestApplicationResourceUrls.pairwiseSectorIdentifierUri())
                        .directAccessGrants())
                .client(ClientBuilder.create().clientId(PUBLIC_TEST_CLIENT)
                        .secret("secret")
                        .redirectUris("http://localhost:8180/auth/realms/master/app/auth/*", "https://localhost:8543/auth/realms/master/app/auth/*")
                        .publicClient())
                .testEventListener()
                .build());

        configureSectorIdentifierRedirectUris();
    }

    private void configureSectorIdentifierRedirectUris() {
        testingClient.testApp().oidcClientEndpoints().setSectorIdentifierRedirectUris(Arrays.asList("http://localhost/resource-server-test", "http://localhost/test-client"));
    }

    @Before
    public void configureAuthorization() throws Exception {
        configureAuthorization(RESOURCE_SERVER_TEST);
        configureAuthorization(PAIRWISE_RESOURCE_SERVER_TEST);
    }

    @After
    public void removeAuthorization() throws Exception {
        removeAuthorization(RESOURCE_SERVER_TEST);
        removeAuthorization(PAIRWISE_RESOURCE_SERVER_TEST);
    }

    @Test
    public void testRptRequestWithoutResourceName() {
        testRptRequestWithoutResourceName(AUTHZ_CLIENT_CONFIG);
    }

    @Test
    public void testRptRequestWithoutResourceNamePairwise() {
        testRptRequestWithoutResourceName(PAIRWISE_AUTHZ_CLIENT_CONFIG);
    }

    public void testRptRequestWithoutResourceName(String configFile) {
        Metadata metadata = new Metadata();

        metadata.setIncludeResourceName(false);

        assertResponse(metadata, () -> {
            AuthorizationRequest request = new AuthorizationRequest();

            request.setMetadata(metadata);
            request.addPermission("Resource 1");

            return getAuthzClient(configFile).authorization("marta", "password").authorize(request);
        });
    }

    @Test
    public void testRptRequestWithResourceName() {
        testRptRequestWithResourceName(AUTHZ_CLIENT_CONFIG);
    }

    @Test
    public void testRptRequestWithResourceNamePairwise() {
        testRptRequestWithResourceName(PAIRWISE_AUTHZ_CLIENT_CONFIG);
    }

    @Test
    public void testInvalidRequestWithClaimsFromConfidentialClient() throws IOException {
        AuthorizationRequest request = new AuthorizationRequest();

        request.addPermission("Resource 13");
        HashMap<Object, Object> obj = new HashMap<>();

        obj.put("claim-a", "claim-a");

        request.setClaimToken(Base64Url.encode(JsonSerialization.writeValueAsBytes(obj)));

        assertResponse(new Metadata(), () -> getAuthzClient(AUTHZ_CLIENT_CONFIG).authorization("marta", "password").authorize(request));
    }

    @Test
    public void testInvalidRequestWithClaimsFromPublicClient() throws IOException {
        oauth.realm("authz-test");
        oauth.clientId(PUBLIC_TEST_CLIENT);

        oauth.doLogin("marta", "password");

        // Token request
        String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);
        OAuthClient.AccessTokenResponse response = oauth.doAccessTokenRequest(code, null);

        AuthorizationRequest request = new AuthorizationRequest();

        request.addPermission("Resource 13");
        HashMap<Object, Object> obj = new HashMap<>();

        obj.put("claim-a", "claim-a");

        request.setClaimToken(Base64Url.encode(JsonSerialization.writeValueAsBytes(obj)));
        this.expectedException.expect(AuthorizationDeniedException.class);
        this.expectedException.expectCause(Matchers.allOf(Matchers.instanceOf(HttpResponseException.class), Matchers.hasProperty("statusCode", Matchers.is(403))));
        this.expectedException.expectMessage("Public clients are not allowed to send claims");
        this.expectedException.reportMissingExceptionWithMessage("Should fail, public clients not allowed");

        getAuthzClient(AUTHZ_CLIENT_CONFIG).authorization(response.getAccessToken()).authorize(request);
    }

    @Test
    public void testRequestWithoutClaimsFromPublicClient() {
        oauth.realm("authz-test");
        oauth.clientId(PUBLIC_TEST_CLIENT);

        oauth.doLogin("marta", "password");

        // Token request
        String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);
        OAuthClient.AccessTokenResponse response = oauth.doAccessTokenRequest(code, null);

        AuthorizationRequest request = new AuthorizationRequest();

        request.addPermission("Resource 13");

        assertResponse(new Metadata(), () -> getAuthzClient(AUTHZ_CLIENT_CONFIG).authorization(response.getAccessToken()).authorize(request));
    }

    @Test
    public void testPermissionLimit() {
        testPermissionLimit(AUTHZ_CLIENT_CONFIG);
    }

    @Test
    public void testPermissionLimitPairwise() {
        testPermissionLimit(PAIRWISE_AUTHZ_CLIENT_CONFIG);
    }

    public void testPermissionLimit(String configFile) {
        AuthorizationRequest request = new AuthorizationRequest();

        for (int i = 1; i <= 10; i++) {
            request.addPermission("Resource " + i);
        }

        Metadata metadata = new Metadata();

        metadata.setLimit(10);

        request.setMetadata(metadata);

        AuthorizationResponse response = getAuthzClient(configFile).authorization("marta", "password").authorize(request);
        AccessToken rpt = toAccessToken(response.getToken());

        List<Permission> permissions = new ArrayList<>(rpt.getAuthorization().getPermissions());

        assertEquals(10, permissions.size());

        for (int i = 0; i < 10; i++) {
            assertEquals("Resource " + (i + 1), permissions.get(i).getResourceName());
        }

        request = new AuthorizationRequest();

        for (int i = 11; i <= 15; i++) {
            request.addPermission("Resource " + i);
        }

        request.setMetadata(metadata);
        request.setRpt(response.getToken());

        response = getAuthzClient(configFile).authorization("marta", "password").authorize(request);
        rpt = toAccessToken(response.getToken());

        permissions = new ArrayList<>(rpt.getAuthorization().getPermissions());

        assertEquals(10, permissions.size());

        for (int i = 0; i < 10; i++) {
            if (i < 5) {
                assertEquals("Resource " + (i + 11), permissions.get(i).getResourceName());
            } else {
                assertEquals("Resource " + (i - 4), permissions.get(i).getResourceName());
            }
        }

        request = new AuthorizationRequest();

        for (int i = 16; i <= 18; i++) {
            request.addPermission("Resource " + i);
        }

        request.setMetadata(metadata);
        request.setRpt(response.getToken());

        response = getAuthzClient(configFile).authorization("marta", "password").authorize(request);
        rpt = toAccessToken(response.getToken());

        permissions = new ArrayList<>(rpt.getAuthorization().getPermissions());

        assertEquals(10, permissions.size());
        assertEquals("Resource 16", permissions.get(0).getResourceName());
        assertEquals("Resource 17", permissions.get(1).getResourceName());
        assertEquals("Resource 18", permissions.get(2).getResourceName());
        assertEquals("Resource 11", permissions.get(3).getResourceName());
        assertEquals("Resource 12", permissions.get(4).getResourceName());
        assertEquals("Resource 13", permissions.get(5).getResourceName());
        assertEquals("Resource 14", permissions.get(6).getResourceName());
        assertEquals("Resource 15", permissions.get(7).getResourceName());
        assertEquals("Resource 1", permissions.get(8).getResourceName());
        assertEquals("Resource 2", permissions.get(9).getResourceName());

        request = new AuthorizationRequest();

        metadata.setLimit(5);
        request.setMetadata(metadata);
        request.setRpt(response.getToken());

        response = getAuthzClient(configFile).authorization("marta", "password").authorize(request);
        rpt = toAccessToken(response.getToken());

        permissions = new ArrayList<>(rpt.getAuthorization().getPermissions());

        assertEquals(5, permissions.size());
        assertEquals("Resource 16", permissions.get(0).getResourceName());
        assertEquals("Resource 17", permissions.get(1).getResourceName());
        assertEquals("Resource 18", permissions.get(2).getResourceName());
        assertEquals("Resource 11", permissions.get(3).getResourceName());
        assertEquals("Resource 12", permissions.get(4).getResourceName());
    }

    @Test
    public void testResourceServerAsAudience() throws Exception {
        testResourceServerAsAudience(
                TEST_CLIENT,
                RESOURCE_SERVER_TEST,
                AUTHZ_CLIENT_CONFIG);
    }

    @Test
    public void testResourceServerAsAudienceWithPairwiseClient() throws Exception {
        testResourceServerAsAudience(
                PAIRWISE_TEST_CLIENT,
                RESOURCE_SERVER_TEST,
                AUTHZ_CLIENT_CONFIG);
    }

    @Test
    public void testPairwiseResourceServerAsAudience() throws Exception {
        testResourceServerAsAudience(
                TEST_CLIENT,
                PAIRWISE_RESOURCE_SERVER_TEST,
                PAIRWISE_AUTHZ_CLIENT_CONFIG);
    }

    @Test
    public void testPairwiseResourceServerAsAudienceWithPairwiseClient() throws Exception {
        testResourceServerAsAudience(
                PAIRWISE_TEST_CLIENT,
                PAIRWISE_RESOURCE_SERVER_TEST,
                PAIRWISE_AUTHZ_CLIENT_CONFIG);
    }

    @Test
    public void testObtainAllEntitlements() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();

        // create a new only owner policy
        JSPolicyRepresentation policy = new JSPolicyRepresentation();
        policy.setName("Only Owner Policy");
        policy.setType("script-scripts/only-owner-policy.js");

        authorization.policies().js().create(policy).close();

        // create a resource that's owned by marta and is managed by the owner
        ResourceRepresentation resource = new ResourceRepresentation();
        resource.setName("Marta Resource");
        resource.setOwner("marta");
        resource.setOwnerManagedAccess(true);

        try (Response response = authorization.resources().create(resource)) {
            resource = response.readEntity(ResourceRepresentation.class);
        }

        // create a resource permission bound to marta's resource and uses the "only owner" policy
        ResourcePermissionRepresentation permission = new ResourcePermissionRepresentation();
        permission.setName("Marta Resource Permission");
        permission.addResource(resource.getId());
        permission.addPolicy(policy.getName());

        authorization.permissions().resource().create(permission).close();

        assertTrue(hasPermission("marta", "password", resource.getId()));
        assertFalse(hasPermission("kolo", "password", resource.getId()));

        // attempt to authorize for Kolo via a permission request. This should create a new ticket on the server that isn't granted
        String accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "kolo", "password").getAccessToken();
        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);
        PermissionResponse permissionResponse = authzClient.protection().permission().create(new PermissionRequest(resource.getId()));
        AuthorizationRequest request = new AuthorizationRequest();
        request.setTicket(permissionResponse.getTicket());

        try {
            authzClient.authorization(accessToken).authorize(request);
        } catch (Exception ignore) {

        }

        List<PermissionTicketRepresentation> tickets = authzClient.protection().permission().findByResource(resource.getId());

        assertEquals(1, tickets.size());

        // after granting the ticket, kolo now can access the resource
        PermissionTicketRepresentation ticket = tickets.get(0);
        ticket.setGranted(true);
        authzClient.protection().permission().update(ticket);
        assertTrue(hasPermission("kolo", "password", resource.getId()));

        // add a new scope to the resource
        resource.addScope("Scope A");
        authorization.resources().resource(resource.getId()).update(resource);

        // the addition of a new scope still grants access to resource and any scope
        assertTrue(hasPermission("kolo", "password", resource.getId()));

        // delete the old ticket. We don't create new tickets when the permission is already granted. And the previous ticket to kolo covers the new scope as well
        authzClient.protection().permission().delete(ticket.getId());

        // create a new ticket requesting for the resource with the new scope
        accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "kolo", "password").getAccessToken();
        permissionResponse = authzClient.protection().permission().create(new PermissionRequest(resource.getId(), "Scope A"));
        request = new AuthorizationRequest();
        request.setTicket(permissionResponse.getTicket());

        try {
            authzClient.authorization(accessToken).authorize(request);
        } catch (Exception ignore) {

        }

        tickets = authzClient.protection().permission().find(resource.getId(), "Scope A", null, null, false, false, null, null);

        assertEquals(1, tickets.size());

        // grant the new ticket with scope
        ticket = tickets.get(0);
        ticket.setGranted(true);
        authzClient.protection().permission().update(ticket);

        // kolo can access
        assertTrue(hasPermission("kolo", "password", resource.getId(), "Scope A"));

        // add another scope to the resource that isn't on the ticket
        resource.addScope("Scope B");
        authorization.resources().resource(resource.getId()).update(resource);

        assertTrue(hasPermission("kolo", "password", resource.getId()));
        assertTrue(hasPermission("kolo", "password", resource.getId(), "Scope A"));
        assertFalse(hasPermission("kolo", "password", resource.getId(), "Scope B"));

        resource.setScopes(new HashSet<>());

        authorization.resources().resource(resource.getId()).update(resource);

        assertFalse(hasPermission("kolo", "password", resource.getId()));
        assertFalse(hasPermission("kolo", "password", resource.getId(), "Scope A"));
        assertFalse(hasPermission("kolo", "password", resource.getId(), "Scope B"));
    }

    @Test
    public void testObtainAllEntitlementsWithLimit() throws Exception {
        org.keycloak.authorization.client.resource.AuthorizationResource authorizationResource = getAuthzClient(AUTHZ_CLIENT_CONFIG).authorization("marta", "password");
        AuthorizationResponse response = authorizationResource.authorize();
        AccessToken accessToken = toAccessToken(response.getToken());
        Authorization authorization = accessToken.getAuthorization();

        assertTrue(authorization.getPermissions().size() >= 20);

        AuthorizationRequest request = new AuthorizationRequest();
        Metadata metadata = new Metadata();

        metadata.setLimit(10);

        request.setMetadata(metadata);

        response = authorizationResource.authorize(request);
        accessToken = toAccessToken(response.getToken());
        authorization = accessToken.getAuthorization();

        assertEquals(10, authorization.getPermissions().size());

        metadata.setLimit(1);

        request.setMetadata(metadata);

        response = authorizationResource.authorize(request);
        accessToken = toAccessToken(response.getToken());
        authorization = accessToken.getAuthorization();

        assertEquals(1, authorization.getPermissions().size());
    }

    @Test
    public void testObtainAllEntitlementsInvalidResource() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();

        JSPolicyRepresentation policy = new JSPolicyRepresentation();

        policy.setName(KeycloakModelUtils.generateId());
        policy.setType("script-scripts/default-policy.js");

        authorization.policies().js().create(policy).close();

        ResourceRepresentation resource = new ResourceRepresentation();

        resource.setName("Sensors");
        resource.addScope("sensors:view", "sensors:update", "sensors:delete");

        authorization.resources().create(resource).close();

        ScopePermissionRepresentation permission = new ScopePermissionRepresentation();

        permission.setName("View Sensor");
        permission.addScope("sensors:view");
        permission.addPolicy(policy.getName());

        authorization.permissions().scope().create(permission).close();

        String accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "kolo", "password").getAccessToken();
        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);
        AuthorizationRequest request = new AuthorizationRequest();

        request.addPermission("Sensortest", "sensors:view");

        getTestContext().getTestingClient().testing().clearEventQueue();
        AccessToken at = toAccessToken(accessToken);

        try {
            authzClient.authorization(accessToken).authorize(request);
            fail("resource is invalid");
        } catch (RuntimeException expected) {
            assertEquals(400, HttpResponseException.class.cast(expected.getCause()).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected.getCause()).toString().contains("invalid_resource"));
        }


        events.expect(EventType.PERMISSION_TOKEN_ERROR).realm(getRealm().toRepresentation().getId()).client(RESOURCE_SERVER_TEST)
                .session((String) null)
                .error("invalid_request")
                .detail("reason", "Resource with id [Sensortest] does not exist.")
                .user(at.getSubject())
                .assertEvent();
    }

    @Test
    public void testObtainAllEntitlementsInvalidScope() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();

        JSPolicyRepresentation policy = new JSPolicyRepresentation();

        policy.setName(KeycloakModelUtils.generateId());
        policy.setType("script-scripts/default-policy.js");

        authorization.policies().js().create(policy).close();

        ResourceRepresentation resource = new ResourceRepresentation();

        resource.setName(KeycloakModelUtils.generateId());
        resource.addScope("sensors:view", "sensors:update", "sensors:delete");

        try (Response response = authorization.resources().create(resource)) {
            resource = response.readEntity(ResourceRepresentation.class);
        }

        ScopePermissionRepresentation permission = new ScopePermissionRepresentation();

        permission.setName(KeycloakModelUtils.generateId());
        permission.addScope("sensors:view");
        permission.addPolicy(policy.getName());

        authorization.permissions().scope().create(permission).close();

        String accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "kolo", "password").getAccessToken();
        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);
        AuthorizationRequest request = new AuthorizationRequest();

        request.addPermission(resource.getId(), "sensors:view_invalid");

        try {
            authzClient.authorization(accessToken).authorize(request);
            fail("scope is invalid");
        } catch (RuntimeException expected) {
            assertEquals(400, HttpResponseException.class.cast(expected.getCause()).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected.getCause()).toString().contains("invalid_scope"));
        }

        request = new AuthorizationRequest();

        request.addPermission(null, "sensors:view_invalid");

        try {
            authzClient.authorization(accessToken).authorize(request);
            fail("scope is invalid");
        } catch (RuntimeException expected) {
            assertEquals(400, HttpResponseException.class.cast(expected.getCause()).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected.getCause()).toString().contains("invalid_scope"));
        }
    }

    @Test
    public void testObtainAllEntitlementsForScope() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();

        JSPolicyRepresentation policy = new JSPolicyRepresentation();

        policy.setName(KeycloakModelUtils.generateId());
        policy.setType("script-scripts/default-policy.js");

        authorization.policies().js().create(policy).close();

        Set<String> resourceIds = new HashSet<>();
        ResourceRepresentation resource = new ResourceRepresentation();

        resource.setName(KeycloakModelUtils.generateId());
        resource.addScope("sensors:view", "sensors:update", "sensors:delete");

        try (Response response = authorization.resources().create(resource)) {
            resourceIds.add(response.readEntity(ResourceRepresentation.class).getId());
        }

        resource = new ResourceRepresentation();

        resource.setName(KeycloakModelUtils.generateId());
        resource.addScope("sensors:view", "sensors:update");

        try (Response response = authorization.resources().create(resource)) {
            resourceIds.add(response.readEntity(ResourceRepresentation.class).getId());
        }

        ScopePermissionRepresentation permission = new ScopePermissionRepresentation();

        permission.setName(KeycloakModelUtils.generateId());
        permission.addScope("sensors:view", "sensors:update");
        permission.addPolicy(policy.getName());

        authorization.permissions().scope().create(permission).close();

        String accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "kolo", "password").getAccessToken();
        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);
        AuthorizationRequest request = new AuthorizationRequest();

        request.addPermission(null, "sensors:view");

        AuthorizationResponse response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        Collection<Permission> permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(2, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertTrue(resourceIds.containsAll(Arrays.asList(grantedPermission.getResourceId())));
            assertEquals(1, grantedPermission.getScopes().size());
            assertTrue(grantedPermission.getScopes().containsAll(Arrays.asList("sensors:view")));
        }

        request.addPermission(null, "sensors:view", "sensors:update");

        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(2, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertTrue(resourceIds.containsAll(Arrays.asList(grantedPermission.getResourceId())));
            assertEquals(2, grantedPermission.getScopes().size());
            assertTrue(grantedPermission.getScopes().containsAll(Arrays.asList("sensors:view", "sensors:update")));
        }

        request.addPermission(null, "sensors:view", "sensors:update", "sensors:delete");

        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(2, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertTrue(resourceIds.containsAll(Arrays.asList(grantedPermission.getResourceId())));
            assertEquals(2, grantedPermission.getScopes().size());
            assertTrue(grantedPermission.getScopes().containsAll(Arrays.asList("sensors:view", "sensors:update")));
        }

        request = new AuthorizationRequest();

        request.addPermission(null, "sensors:view");
        request.addPermission(null, "sensors:update");

        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(2, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertTrue(resourceIds.containsAll(Arrays.asList(grantedPermission.getResourceId())));
            assertEquals(2, grantedPermission.getScopes().size());
            assertTrue(grantedPermission.getScopes().containsAll(Arrays.asList("sensors:view", "sensors:update")));
        }
    }

    @Test
    public void testObtainAllEntitlementsForScopeWithDeny() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();

        JSPolicyRepresentation policy = new JSPolicyRepresentation();

        policy.setName(KeycloakModelUtils.generateId());
        policy.setType("script-scripts/default-policy.js");

        authorization.policies().js().create(policy).close();

        authorization.scopes().create(new ScopeRepresentation("sensors:view")).close();

        ScopePermissionRepresentation permission = new ScopePermissionRepresentation();

        permission.setName(KeycloakModelUtils.generateId());
        permission.addScope("sensors:view");
        permission.addPolicy(policy.getName());

        authorization.permissions().scope().create(permission).close();

        String accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "kolo", "password").getAccessToken();
        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);
        AuthorizationRequest request = new AuthorizationRequest();

        request.addPermission(null, "sensors:view");

        AuthorizationResponse response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        Collection<Permission> permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertNull(grantedPermission.getResourceId());
            assertEquals(1, grantedPermission.getScopes().size());
            assertTrue(grantedPermission.getScopes().containsAll(Arrays.asList("sensors:view")));
        }
    }

    @Test
    public void testObtainAllEntitlementsForResourceWithResourcePermission() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();

        JSPolicyRepresentation policy = new JSPolicyRepresentation();

        policy.setName(KeycloakModelUtils.generateId());
        policy.setType("script-scripts/default-policy.js");

        authorization.policies().js().create(policy).close();

        ResourceRepresentation resource = new ResourceRepresentation();

        resource.setName(KeycloakModelUtils.generateId());
        resource.addScope("scope:view", "scope:update", "scope:delete");

        try (Response response = authorization.resources().create(resource)) {
            resource = response.readEntity(ResourceRepresentation.class);
        }

        ResourcePermissionRepresentation permission = new ResourcePermissionRepresentation();

        permission.setName(KeycloakModelUtils.generateId());
        permission.addResource(resource.getId());
        permission.addPolicy(policy.getName());

        authorization.permissions().resource().create(permission).close();

        String accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "kolo", "password").getAccessToken();
        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);
        AuthorizationRequest request = new AuthorizationRequest();

        request.addPermission(null, "scope:view", "scope:update", "scope:delete");

        AuthorizationResponse response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        Collection<Permission> permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(resource.getId(), grantedPermission.getResourceId());
            assertEquals(3, grantedPermission.getScopes().size());
            assertTrue(grantedPermission.getScopes().containsAll(Arrays.asList("scope:view")));
        }

        resource.setScopes(new HashSet<>());
        resource.addScope("scope:view", "scope:update");

        authorization.resources().resource(resource.getId()).update(resource);

        request = new AuthorizationRequest();

        request.addPermission(null, "scope:view", "scope:update", "scope:delete");

        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(resource.getId(), grantedPermission.getResourceId());
            assertEquals(2, grantedPermission.getScopes().size());
            assertTrue(grantedPermission.getScopes().containsAll(Arrays.asList("scope:view", "scope:update")));
        }

        request = new AuthorizationRequest();

        request.addPermission(resource.getId(), "scope:view", "scope:update", "scope:delete");

        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(resource.getId(), grantedPermission.getResourceId());
            assertEquals(2, grantedPermission.getScopes().size());
            assertTrue(grantedPermission.getScopes().containsAll(Arrays.asList("scope:view", "scope:update")));
        }
    }

    @Test
    public void testObtainAllEntitlementsForResourceWithScopePermission() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();

        JSPolicyRepresentation policy = new JSPolicyRepresentation();

        policy.setName(KeycloakModelUtils.generateId());
        policy.setType("script-scripts/default-policy.js");

        authorization.policies().js().create(policy).close();

        ResourceRepresentation resourceWithoutType = new ResourceRepresentation();

        resourceWithoutType.setName(KeycloakModelUtils.generateId());
        resourceWithoutType.addScope("scope:view", "scope:update", "scope:delete");

        try (Response response = authorization.resources().create(resourceWithoutType)) {
            resourceWithoutType = response.readEntity(ResourceRepresentation.class);
        }

        ResourceRepresentation resourceWithType = new ResourceRepresentation();

        resourceWithType.setName(KeycloakModelUtils.generateId());
        resourceWithType.setType("type-one");
        resourceWithType.addScope("scope:view", "scope:update", "scope:delete");

        try (Response response = authorization.resources().create(resourceWithType)) {
            resourceWithType = response.readEntity(ResourceRepresentation.class);
        }

        ScopePermissionRepresentation permission = new ScopePermissionRepresentation();

        permission.setName(KeycloakModelUtils.generateId());
        permission.addResource(resourceWithoutType.getId());
        permission.addScope("scope:view");
        permission.addPolicy(policy.getName());

        authorization.permissions().scope().create(permission).close();

        permission = new ScopePermissionRepresentation();

        permission.setName(KeycloakModelUtils.generateId());
        permission.setResourceType("type-one");
        permission.addScope("scope:update");
        permission.addPolicy(policy.getName());

        authorization.permissions().scope().create(permission).close();

        String accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "kolo", "password").getAccessToken();
        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);

        AuthorizationRequest request = new AuthorizationRequest();
        request.addPermission(resourceWithoutType.getId(), "scope:view", "scope:update", "scope:delete");

        AuthorizationResponse response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        Collection<Permission> permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(resourceWithoutType.getId(), grantedPermission.getResourceId());
            assertEquals(1, grantedPermission.getScopes().size());
            assertTrue(grantedPermission.getScopes().containsAll(Arrays.asList("scope:view")));
        }

        request = new AuthorizationRequest();
        request.addPermission(resourceWithType.getId(), "scope:view", "scope:update", "scope:delete");

        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(resourceWithType.getId(), grantedPermission.getResourceId());
            assertEquals(1, grantedPermission.getScopes().size());
            assertTrue(grantedPermission.getScopes().containsAll(Arrays.asList("scope:update")));
        }
    }

    @Test
    public void testServerDecisionStrategy() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();

        ResourceRepresentation resource = new ResourceRepresentation();

        resource.setName(KeycloakModelUtils.generateId());
        resource.addScope("read", "write", "delete");

        try (Response response = authorization.resources().create(resource)) {
            resource = response.readEntity(ResourceRepresentation.class);
        }

        // create a policy that always grants access for everyone
        JSPolicyRepresentation grantPolicy = new JSPolicyRepresentation();
        grantPolicy.setName(KeycloakModelUtils.generateId());
        grantPolicy.setType("script-scripts/default-policy.js");

        authorization.policies().js().create(grantPolicy).close();

        // create a policy that always denies access for everyone
        JSPolicyRepresentation denyPolicy = new JSPolicyRepresentation();
        denyPolicy.setName(KeycloakModelUtils.generateId());
        denyPolicy.setType("script-scripts/always-deny-policy.js");

        authorization.policies().js().create(denyPolicy).close();

        // create a resource permission tied to our created resource that always denies
        ResourcePermissionRepresentation resourcePermission = new ResourcePermissionRepresentation();
        resourcePermission.setName(KeycloakModelUtils.generateId());
        resourcePermission.addResource(resource.getId());
        resourcePermission.addPolicy(denyPolicy.getName());

        authorization.permissions().resource().create(resourcePermission).close();

        // kolo tries to access the resource by id/name with no scopes and gets rejected
        String accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "kolo", "password").getAccessToken();
        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);
        AuthorizationRequest request = new AuthorizationRequest();
        request.addPermission(resource.getName());
        try {
            authzClient.authorization(accessToken).authorize(request);
            fail("kolo can not access the resource");
        } catch (RuntimeException expected) {
            assertEquals(403, HttpResponseException.class.cast(expected.getCause()).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected.getCause()).toString().contains("access_denied"));
        }

        // create a resource permission tied to our created resource that always grants -- this will conflict with the always denies by design
        ResourcePermissionRepresentation resourcePermission1 = new ResourcePermissionRepresentation();
        resourcePermission1.setName(KeycloakModelUtils.generateId());
        resourcePermission1.addResource(resource.getId());
        resourcePermission1.addPolicy(grantPolicy.getName());

        authorization.permissions().resource().create(resourcePermission1).close();

        // set the resource server to affirmative mode, should now allow access to the resource on the read scope (resource and scope policies have the same priority so one needs to pass)
        ResourceServerRepresentation settings = authorization.getSettings();
        settings.setDecisionStrategy(DecisionStrategy.AFFIRMATIVE);
        authorization.update(settings);

        // a resource permission will grant all scopes on the resource in affirmative mode
        assertPermissions(authzClient, accessToken, request, resource, "read", "write", "delete");

        // go back to unanimous mode -- conflicting policies should now result in no scopes being granted
        settings.setDecisionStrategy(DecisionStrategy.UNANIMOUS);
        authorization.update(settings);

        // create a scope permission that's tied to the "read" scope that always grants
        // we have to tie this to the resource as well, otherwise the resource permission will take priority
        // type -> scope -> resource -> scope + resource -- this pattern is followed in other areas of the code as well (increasing specificity)
        // the override rules work so that you can also use higher priority policies to deny --
        // for instance maybe everyone can access resource of "type" but kolo is not allowed to access specifically resourceA
        ScopePermissionRepresentation scopePermission1 = new ScopePermissionRepresentation();
        scopePermission1.setName(KeycloakModelUtils.generateId());
        scopePermission1.addScope("read");
        scopePermission1.addResource(resource.getId());
        scopePermission1.addPolicy(grantPolicy.getName());

        ScopePermissionsResource scopePermissions = authorization.permissions().scope();
        scopePermissions.create(scopePermission1).close();

        // add the "delete" permission to the scope permission we set up before
        scopePermission1 = scopePermissions.findByName(scopePermission1.getName());
        scopePermission1.addScope("read", "delete");
        scopePermissions.findById(scopePermission1.getId()).update(scopePermission1);

        // by override rules, our policy should bypass the deny resource policy and grant us the two scopes we asked for
        assertPermissions(authzClient, accessToken, request, resource, "read", "delete");

        // create another scope permission that grants "write" scope and make it specific to the resource as well
        ScopePermissionRepresentation scopePermission2 = new ScopePermissionRepresentation();
        scopePermission2.setName(KeycloakModelUtils.generateId());
        scopePermission2.addScope("write");
        scopePermission2.addResource(resource.getId());
        scopePermission2.addPolicy(grantPolicy.getName());
        scopePermissions.create(scopePermission2).close();

        assertPermissions(authzClient, accessToken, request, resource, "read", "delete", "write");

        ScopePermissionRepresentation scopePermission3 = new ScopePermissionRepresentation();
        scopePermission3.setName(KeycloakModelUtils.generateId());
        scopePermission3.addResource(resource.getId());
        scopePermission3.addScope("write", "read", "delete");
        scopePermission3.addPolicy(grantPolicy.getName());

        scopePermissions.create(scopePermission3).close();

        assertPermissions(authzClient, accessToken, request, resource, "read", "delete", "write");

        // remove now that we have a scope with all 3 permissions and make sure access stays
        scopePermission2 = scopePermissions.findByName(scopePermission2.getName());
        scopePermissions.findById(scopePermission2.getId()).remove();

        assertPermissions(authzClient, accessToken, request, resource, "read", "delete", "write");

        scopePermission1 = scopePermissions.findByName(scopePermission1.getName());
        scopePermissions.findById(scopePermission1.getId()).remove();

        // same thing again, scope 3 should be granting access
        assertPermissions(authzClient, accessToken, request, resource, "read", "delete", "write");

        scopePermission3 = scopePermissions.findByName(scopePermission3.getName());
        
        scopePermission3.addScope("write", "delete");
        scopePermissions.findById(scopePermission3.getId()).update(scopePermission3);

        assertPermissions(authzClient, accessToken, request, resource, "delete", "write");
        
        scopePermissions.findById(scopePermission3.getId()).remove();
        // delete the last permission to the resource and check that kolo can't access anymore
        try {
            authzClient.authorization(accessToken).authorize(request);
            fail("kolo can not access the resource");
        } catch (RuntimeException expected) {
            assertEquals(403, HttpResponseException.class.cast(expected.getCause()).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected.getCause()).toString().contains("access_denied"));
        }

        // switch back to affirmative mode and verify that kolo can still access the resource due to the previous resource permissions
        settings.setDecisionStrategy(DecisionStrategy.AFFIRMATIVE);
        authorization.update(settings);

        assertPermissions(authzClient, accessToken, request, resource, "read", "delete", "write");
        
        settings.setDecisionStrategy(DecisionStrategy.UNANIMOUS);
        authorization.update(settings);

        try {
            authzClient.authorization(accessToken).authorize(request);
            fail("kolo can not access the resource");
        } catch (RuntimeException expected) {
            assertEquals(403, HttpResponseException.class.cast(expected.getCause()).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected.getCause()).toString().contains("access_denied"));
        }
    }

    @Test
    public void testObtainAllEntitlementsForResourceType() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();

        JSPolicyRepresentation policy = new JSPolicyRepresentation();

        policy.setName(KeycloakModelUtils.generateId());
        policy.setType("script-scripts/default-policy.js");

        authorization.policies().js().create(policy).close();

        for (int i = 0; i < 10; i++) {
            ResourceRepresentation resource = new ResourceRepresentation();

            resource.setType("type-one");
            resource.setName(KeycloakModelUtils.generateId());

            authorization.resources().create(resource).close();
        }

        for (int i = 0; i < 10; i++) {
            ResourceRepresentation resource = new ResourceRepresentation();

            resource.setType("type-two");
            resource.setName(KeycloakModelUtils.generateId());

            authorization.resources().create(resource).close();
        }

        for (int i = 0; i < 10; i++) {
            ResourceRepresentation resource = new ResourceRepresentation();

            resource.setType("type-three");
            resource.setName(KeycloakModelUtils.generateId());

            authorization.resources().create(resource).close();
        }

        for (int i = 0; i < 10; i++) {
            ResourceRepresentation resource = new ResourceRepresentation();

            resource.setType("type-four");
            resource.setName(KeycloakModelUtils.generateId());
            resource.addScope("scope:view", "scope:update");

            authorization.resources().create(resource).close();
        }

        for (int i = 0; i < 10; i++) {
            ResourceRepresentation resource = new ResourceRepresentation();

            resource.setType("type-five");
            resource.setName(KeycloakModelUtils.generateId());
            resource.addScope("scope:view");

            authorization.resources().create(resource).close();
        }


        ResourcePermissionRepresentation resourcePermission = new ResourcePermissionRepresentation();

        resourcePermission.setName(KeycloakModelUtils.generateId());
        resourcePermission.setResourceType("type-one");
        resourcePermission.addPolicy(policy.getName());

        authorization.permissions().resource().create(resourcePermission).close();

        resourcePermission = new ResourcePermissionRepresentation();

        resourcePermission.setName(KeycloakModelUtils.generateId());
        resourcePermission.setResourceType("type-two");
        resourcePermission.addPolicy(policy.getName());

        authorization.permissions().resource().create(resourcePermission).close();

        resourcePermission = new ResourcePermissionRepresentation();

        resourcePermission.setName(KeycloakModelUtils.generateId());
        resourcePermission.setResourceType("type-three");
        resourcePermission.addPolicy(policy.getName());

        authorization.permissions().resource().create(resourcePermission).close();

        ScopePermissionRepresentation scopePersmission = new ScopePermissionRepresentation();

        scopePersmission.setName(KeycloakModelUtils.generateId());
        scopePersmission.setResourceType("type-four");
        scopePersmission.addScope("scope:view");
        scopePersmission.addPolicy(policy.getName());

        authorization.permissions().scope().create(scopePersmission).close();

        String accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "kolo", "password").getAccessToken();
        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);

        AuthorizationRequest request = new AuthorizationRequest();
        request.addPermission("resource-type:type-one");
        AuthorizationResponse response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        Collection<Permission> permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(10, permissions.size());

        request = new AuthorizationRequest();
        request.addPermission("resource-type:type-three");
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(10, permissions.size());

        request = new AuthorizationRequest();
        request.addPermission("resource-type:type-four", "scope:view");
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(10, permissions.size());
        for (Permission grantedPermission : permissions) {
            assertEquals(1, grantedPermission.getScopes().size());
            assertTrue(grantedPermission.getScopes().containsAll(Arrays.asList("scope:view")));
        }

        request = new AuthorizationRequest();
        request.addPermission("resource-type:type-five", "scope:view");
        try {
            authzClient.authorization(accessToken).authorize(request);
            fail("no type-five resources can be granted since scope permission for scope:view only applies to type-four");
        } catch (RuntimeException expected) {
            assertEquals(403, HttpResponseException.class.cast(expected.getCause()).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected.getCause()).toString().contains("access_denied"));
        }

        for (int i = 0; i < 5; i++) {
            ResourceRepresentation resource = new ResourceRepresentation();

            resource.setOwner("kolo");
            resource.setType("type-two");
            resource.setName(KeycloakModelUtils.generateId());

            authorization.resources().create(resource).close();
        }

        request = new AuthorizationRequest();
        request.addPermission("resource-type-any:type-two");
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(15, permissions.size());

        request = new AuthorizationRequest();
        request.addPermission("resource-type-owner:type-two");
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(5, permissions.size());

        request = new AuthorizationRequest();
        request.addPermission("resource-type-instance:type-two");
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(5, permissions.size());

        Permission next = permissions.iterator().next();

        ResourceResource resourceMgmt = client.authorization().resources().resource(next.getResourceId());
        ResourceRepresentation representation = resourceMgmt.toRepresentation();

        representation.setType("type-three");

        resourceMgmt.update(representation);

        request = new AuthorizationRequest();
        request.addPermission("resource-type-instance:type-two");
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(4, permissions.size());

        request = new AuthorizationRequest();
        request.addPermission("resource-type-instance:type-three");
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        request = new AuthorizationRequest();
        request.addPermission("resource-type-any:type-three");
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(11, permissions.size());

        for (int i = 0; i < 2; i++) {
            ResourceRepresentation resource = new ResourceRepresentation();

            resource.setOwner("marta");
            resource.setType("type-one");
            resource.setName(KeycloakModelUtils.generateId());

            authorization.resources().create(resource).close();
        }

        request = new AuthorizationRequest();
        request.addPermission("resource-type:type-one");
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(10, permissions.size());

        accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "marta", "password").getAccessToken();

        request = new AuthorizationRequest();
        request.addPermission("resource-type-owner:type-one");
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(2, permissions.size());

        request = new AuthorizationRequest();
        request.addPermission("resource-type-instance:type-one");
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(2, permissions.size());

        request = new AuthorizationRequest();
        request.addPermission("resource-type-any:type-one");
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(12, permissions.size());
    }

    @Test
    public void testOverridePermission() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();
        JSPolicyRepresentation onlyOwnerPolicy = createOnlyOwnerPolicy();

        authorization.policies().js().create(onlyOwnerPolicy).close();

        ResourceRepresentation typedResource = new ResourceRepresentation();

        typedResource.setType("resource");
        typedResource.setName(KeycloakModelUtils.generateId());
        typedResource.addScope("read", "update");

        try (Response response = authorization.resources().create(typedResource)) {
            typedResource = response.readEntity(ResourceRepresentation.class);
        }

        ResourcePermissionRepresentation typedResourcePermission = new ResourcePermissionRepresentation();

        // this type resource permission should apply to all resources of type "resource" and have lowest priority
        typedResourcePermission.setName(KeycloakModelUtils.generateId());
        typedResourcePermission.setResourceType("resource");
        typedResourcePermission.addPolicy(onlyOwnerPolicy.getName());

        try (Response response = authorization.permissions().resource().create(typedResourcePermission)) {
            typedResourcePermission = response.readEntity(ResourcePermissionRepresentation.class);
        }

        ResourceRepresentation martaResource = new ResourceRepresentation();
        martaResource.setType("resource");
        martaResource.setName(KeycloakModelUtils.generateId());
        martaResource.addScope("read", "update");
        martaResource.setOwner("marta");

        try (Response response = authorization.resources().create(martaResource)) {
            martaResource = response.readEntity(ResourceRepresentation.class);
        }

        String accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "marta", "password").getAccessToken();
        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);
        AuthorizationRequest request = new AuthorizationRequest();

        request.addPermission(martaResource.getName());

        // marta can access her resource due to the "only owner" permission on all resource types
        AuthorizationResponse response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        Collection<Permission> permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(martaResource.getName(), grantedPermission.getResourceName());
            Set<String> scopes = grantedPermission.getScopes();
            assertEquals(2, scopes.size());
            assertThat(scopes, Matchers.containsInAnyOrder("read", "update"));
        }

        accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "kolo", "password").getAccessToken();
        authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);

        request = new AuthorizationRequest();

        request.addPermission(martaResource.getId());

        // likewise, kolo cannot access marta's resource due to the resource permission in place
        try {
            authzClient.authorization(accessToken).authorize(request);
            fail("kolo can not access marta resource");
        } catch (RuntimeException expected) {
            assertEquals(403, HttpResponseException.class.cast(expected.getCause()).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected.getCause()).toString().contains("access_denied"));
        }

        // Create a policy that would only allow kolo access
        UserPolicyRepresentation onlyKoloPolicy = new UserPolicyRepresentation();
        onlyKoloPolicy.setName(KeycloakModelUtils.generateId());
        onlyKoloPolicy.addUser("kolo");

        authorization.policies().user().create(onlyKoloPolicy).close();

        // Create a resource permission that allows kolo access to the resource.
        // This permission should override the type permission we set originally for only owner
        ResourcePermissionRepresentation martaResourcePermission = new ResourcePermissionRepresentation();
        martaResourcePermission.setName(KeycloakModelUtils.generateId());
        martaResourcePermission.addResource(martaResource.getId());
        martaResourcePermission.addPolicy(onlyKoloPolicy.getName());

        try (Response response1 = authorization.permissions().resource().create(martaResourcePermission)) {
            martaResourcePermission = response1.readEntity(ResourcePermissionRepresentation.class);
        }

        // try to access the resource with kolo's access token and see that everything is working as expected with overrides
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());
        
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(martaResource.getName(), grantedPermission.getResourceName());
            Set<String> scopes = grantedPermission.getScopes();
            assertEquals(2, scopes.size());
            assertThat(scopes, Matchers.containsInAnyOrder("read", "update"));
        }

        // disassociate the type permission with the "resource" type, making it untyped.
        // link the permission to the typed resource (not marta's resource)
        // only owner can access typed resource after the update to this permission
        // that resource has no owner and no other policies associated with it
        typedResourcePermission.setResourceType(null);
        typedResourcePermission.addResource(typedResource.getName());

        authorization.permissions().resource().findById(typedResourcePermission.getId()).update(typedResourcePermission);

        // after removing the permission kolo can still access Marta's resource as matching the established policy
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(martaResource.getName(), grantedPermission.getResourceName());
            Set<String> scopes = grantedPermission.getScopes();
            assertEquals(2, scopes.size());
            assertThat(scopes, Matchers.containsInAnyOrder("read", "update"));
        }

        // Set a policy on marta's resource for the "update" scope that only allows the owner to update
        // this policy should take a higher priority than the policy allowing kolo access with no specified scopes
        ScopePermissionRepresentation martaResourceUpdatePermission = new ScopePermissionRepresentation();
        martaResourceUpdatePermission.setName(KeycloakModelUtils.generateId());
        martaResourceUpdatePermission.addResource(martaResource.getId());
        martaResourceUpdatePermission.addScope("update");
        martaResourceUpdatePermission.addPolicy(onlyOwnerPolicy.getName());

        try (Response response1 = authorization.permissions().scope().create(martaResourceUpdatePermission)) {
            martaResourceUpdatePermission = response1.readEntity(ScopePermissionRepresentation.class);
        }

        // now kolo can only read, but not update because update is tied to the "only owner" policy
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(martaResource.getName(), grantedPermission.getResourceName());
            Set<String> scopes = grantedPermission.getScopes();
            assertEquals(1, scopes.size());
            assertThat(scopes, Matchers.containsInAnyOrder("read"));
        }

        authorization.permissions().resource().findById(martaResourcePermission.getId()).remove();

        try {
            // after removing permission to marta resource, kolo can not access any scope in the resource
            authzClient.authorization(accessToken).authorize(request);
            fail("kolo can not access marta resource");
        } catch (RuntimeException expected) {
            assertEquals(403, HttpResponseException.class.cast(expected.getCause()).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected.getCause()).toString().contains("access_denied"));
        }

        // add the policy "only kolo" to the marta scope permission that allows for the "update" scope to be accessed.
        // Set affirmative mode so that only one of the two policies has to pass: either kolo or owner
        martaResourceUpdatePermission.addPolicy(onlyKoloPolicy.getName());
        // change the decision strategy to affirmative, so only one of the associated policies has to pass
        martaResourceUpdatePermission.setDecisionStrategy(DecisionStrategy.AFFIRMATIVE);
        authorization.permissions().scope().findById(martaResourceUpdatePermission.getId()).update(martaResourceUpdatePermission);

        // now kolo can access because update permission changed to allow him to access the resource using an affirmative strategy
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(martaResource.getName(), grantedPermission.getResourceName());
            Set<String> scopes = grantedPermission.getScopes();
            assertEquals(1, scopes.size());
            assertThat(scopes, Matchers.containsInAnyOrder("update"));
        }

        accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "marta", "password").getAccessToken();

        // marta can still access her resource. She also only has access to the update scope though. read scope was removed earlier when we deleted the type permission
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(martaResource.getName(), grantedPermission.getResourceName());
            Set<String> scopes = grantedPermission.getScopes();
            assertEquals(1, scopes.size());
            assertThat(scopes, Matchers.containsInAnyOrder("update"));
        }

        // remove the update permission from the resource that allows kolo and marta access
        authorization.permissions().scope().findById(martaResourceUpdatePermission.getId()).remove();
        accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "kolo", "password").getAccessToken();

        try {
            // back to original setup, permissions not granted by the type resource
            authzClient.authorization(accessToken).authorize(request);
            fail("kolo can not access marta resource");
        } catch (RuntimeException expected) {
            assertEquals(403, HttpResponseException.class.cast(expected.getCause()).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected.getCause()).toString().contains("access_denied"));
        }
    }

    @Test
    public void testOverrideParentScopePermission() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();
        JSPolicyRepresentation onlyOwnerPolicy = createOnlyOwnerPolicy();

        authorization.policies().js().create(onlyOwnerPolicy).close();

        // create a typed resource with two scopes
        ResourceRepresentation typedResource = new ResourceRepresentation();
        typedResource.setType("resource");
        typedResource.setName(KeycloakModelUtils.generateId());
        typedResource.addScope("read", "update");

        try (Response response = authorization.resources().create(typedResource)) {
            typedResource = response.readEntity(ResourceRepresentation.class);
        }

        // create a scope permission on the type "resource" that covers the two scopes, only allows the owner
        ScopePermissionRepresentation typedResourcePermission = new ScopePermissionRepresentation();
        typedResourcePermission.setType("resource");
        typedResourcePermission.setName(KeycloakModelUtils.generateId());
        typedResourcePermission.addPolicy(onlyOwnerPolicy.getName());
        typedResourcePermission.addScope("read", "update");

        authorization.permissions().scope().create(typedResourcePermission).close();

        // create a resource owned by Marta with the "read" scope and is also a "resource" type
        ResourceRepresentation martaResource = new ResourceRepresentation();
        martaResource.setType("resource");
        martaResource.setName(KeycloakModelUtils.generateId());
        martaResource.addScope("read");
        martaResource.setOwner("marta");

        try (Response response = authorization.resources().create(martaResource)) {
            martaResource = response.readEntity(ResourceRepresentation.class);
        }

        String accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "marta", "password").getAccessToken();
        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);

        AuthorizationRequest request = new AuthorizationRequest();
        request.addPermission(martaResource.getName());

        // marta can access her resource based on the type policy
        AuthorizationResponse response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        Collection<Permission> permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(martaResource.getName(), grantedPermission.getResourceName());
            Set<String> scopes = grantedPermission.getScopes();
            assertEquals(2, scopes.size());
            assertThat(scopes, Matchers.containsInAnyOrder("read", "update"));
        }

        // try to have kolo access Marta's resource and fail
        accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "kolo", "password").getAccessToken();
        authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);
        request = new AuthorizationRequest();
        request.addPermission(martaResource.getId());
        try {
            authzClient.authorization(accessToken).authorize(request);
            fail("kolo can not access marta resource");
        } catch (RuntimeException expected) {
            assertEquals(403, HttpResponseException.class.cast(expected.getCause()).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected.getCause()).toString().contains("access_denied"));
        }

        // Create a policy that allows kolo access
        UserPolicyRepresentation onlyKoloPolicy = new UserPolicyRepresentation();
        onlyKoloPolicy.setName(KeycloakModelUtils.generateId());
        onlyKoloPolicy.addUser("kolo");
        authorization.policies().user().create(onlyKoloPolicy).close();

        // Add the policy to a permission that's bound to Marta's resource
        ResourcePermissionRepresentation martaResourcePermission = new ResourcePermissionRepresentation();
        martaResourcePermission.setName(KeycloakModelUtils.generateId());
        martaResourcePermission.addResource(martaResource.getId());
        martaResourcePermission.addPolicy(onlyKoloPolicy.getName());

        try (Response response1 = authorization.permissions().resource().create(martaResourcePermission)) {
            martaResourcePermission = response1.readEntity(ResourcePermissionRepresentation.class);
        }

        // kolo can now access the resource
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(martaResource.getName(), grantedPermission.getResourceName());
            Set<String> scopes = grantedPermission.getScopes();
            assertEquals(2, scopes.size());
            assertThat(scopes, Matchers.containsInAnyOrder("read", "update"));
        }

        // Create a scope permission that is associated with the scope "update" and the resource marta resource
        // Associate the "only owner" policy with this scope permission
        // Expect that kolo can now only access the "read" scope on the marta resource
        ScopePermissionRepresentation martaResourceUpdatePermission = new ScopePermissionRepresentation();
        martaResourceUpdatePermission.setName(KeycloakModelUtils.generateId());
        martaResourceUpdatePermission.addResource(martaResource.getId());
        martaResourceUpdatePermission.addScope("update");
        martaResourceUpdatePermission.addPolicy(onlyOwnerPolicy.getName());

        try (Response response1 = authorization.permissions().scope().create(martaResourceUpdatePermission)) {
            martaResourceUpdatePermission = response1.readEntity(ScopePermissionRepresentation.class);
        }

        // now kolo can only read, but not update
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(martaResource.getName(), grantedPermission.getResourceName());
            Set<String> scopes = grantedPermission.getScopes();
            assertEquals(1, scopes.size());
            assertThat(scopes, Matchers.containsInAnyOrder("read"));
        }

        authorization.permissions().resource().findById(martaResourcePermission.getId()).remove();

        try {
            // after removing permission to marta resource, kolo can not access any scope in the resource
            authzClient.authorization(accessToken).authorize(request);
            fail("kolo can not access marta resource");
        } catch (RuntimeException expected) {
            assertEquals(403, HttpResponseException.class.cast(expected.getCause()).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected.getCause()).toString().contains("access_denied"));
        }

        // add the only kolo policy to the update scope, conflicts with "only owner"
        martaResourceUpdatePermission.addPolicy(onlyKoloPolicy.getName());
        // set to affirmative so that only one permission has to pass and not both
        martaResourceUpdatePermission.setDecisionStrategy(DecisionStrategy.AFFIRMATIVE);

        authorization.permissions().scope().findById(martaResourceUpdatePermission.getId()).update(martaResourceUpdatePermission);

        // now kolo can access because update permission changed to allow him to access the resource using an affirmative strategy
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(martaResource.getName(), grantedPermission.getResourceName());
            Set<String> scopes = grantedPermission.getScopes();
            assertEquals(1, scopes.size());
            assertThat(scopes, Matchers.containsInAnyOrder("update"));
        }

        accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "marta", "password").getAccessToken();

        // marta can still access her resource
        response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(martaResource.getName(), grantedPermission.getResourceName());
            Set<String> scopes = grantedPermission.getScopes();
            assertEquals(2, scopes.size());
            assertThat(scopes, Matchers.containsInAnyOrder("update", "read"));
        }

        authorization.permissions().scope().findById(martaResourceUpdatePermission.getId()).remove();
        accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "kolo", "password").getAccessToken();

        try {
            // back to original setup, permissions not granted by the type resource
            authzClient.authorization(accessToken).authorize(request);
            fail("kolo can not access marta resource");
        } catch (RuntimeException expected) {
            assertEquals(403, HttpResponseException.class.cast(expected.getCause()).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected.getCause()).toString().contains("access_denied"));
        }
    }

    @NotNull
    private JSPolicyRepresentation createOnlyOwnerPolicy() {
        JSPolicyRepresentation onlyOwnerPolicy = new JSPolicyRepresentation();

        onlyOwnerPolicy.setName(KeycloakModelUtils.generateId());
        onlyOwnerPolicy.setType("script-scripts/only-owner-policy.js");

        return onlyOwnerPolicy;
    }

    @Test
    public void testPermissionsWithResourceAttributes() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();
        JSPolicyRepresentation onlyPublicResourcesPolicy = new JSPolicyRepresentation();

        onlyPublicResourcesPolicy.setName(KeycloakModelUtils.generateId());
        onlyPublicResourcesPolicy.setType("script-scripts/resource-visibility-attribute-policy.js");

        authorization.policies().js().create(onlyPublicResourcesPolicy).close();

        JSPolicyRepresentation onlyOwnerPolicy = createOnlyOwnerPolicy();

        authorization.policies().js().create(onlyOwnerPolicy).close();

        ResourceRepresentation typedResource = new ResourceRepresentation();

        typedResource.setType("resource");
        typedResource.setName(KeycloakModelUtils.generateId());

        try (Response response = authorization.resources().create(typedResource)) {
            typedResource = response.readEntity(ResourceRepresentation.class);
        }

        // create a resource owned by kolo with private visibility
        ResourceRepresentation userResource = new ResourceRepresentation();
        userResource.setName(KeycloakModelUtils.generateId());
        userResource.setType("resource");
        userResource.setOwner("kolo");
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("visibility", Arrays.asList("private"));
        userResource.setAttributes(attributes);

        try (Response response = authorization.resources().create(userResource)) {
            userResource = response.readEntity(ResourceRepresentation.class);
        }

        // Create a resource permission for all "resource" types that only allows public resources to be accessed
        ResourcePermissionRepresentation typedResourcePermission = new ResourcePermissionRepresentation();
        typedResourcePermission.setName(KeycloakModelUtils.generateId());
        typedResourcePermission.setResourceType("resource");
        typedResourcePermission.addPolicy(onlyPublicResourcesPolicy.getName());

        try (Response response = authorization.permissions().resource().create(typedResourcePermission)) {
            typedResourcePermission = response.readEntity(ResourcePermissionRepresentation.class);
        }

        // marta can access any public resource and resources that she owns. She doesn't own any so only the one resource will show
        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);

        AuthorizationRequest request = new AuthorizationRequest();
        request.addPermission(typedResource.getId());
        request.addPermission(userResource.getId());

        AuthorizationResponse response = authzClient.authorization("marta", "password").authorize(request);
        assertNotNull(response.getToken());
        Collection<Permission> permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(typedResource.getName(), grantedPermission.getResourceName());
        }

        // kolo can access the resource they own in addition to the public resource
        response = authzClient.authorization("kolo", "password").authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        // we now set an only owner policy on this which will conflict with the "only public" policy
        typedResourcePermission.addPolicy(onlyOwnerPolicy.getName());
        // set to affirmative so only one of the policies has to pass
        typedResourcePermission.setDecisionStrategy(DecisionStrategy.AFFIRMATIVE);

        authorization.permissions().resource().findById(typedResourcePermission.getId()).update(typedResourcePermission);

        // The resource still isn't owned by marta, so we should expect access to only one resource
        response = authzClient.authorization("marta", "password").authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(typedResource.getName(), grantedPermission.getResourceName());
        }

        // now we set the typed resource to private as we did with the resource owned by kolo
        typedResource.setAttributes(attributes);

        authorization.resources().resource(typedResource.getId()).update(typedResource);

        // marta does not own either resource and both are private, we expect this query to fail
        try {
            authzClient.authorization("marta", "password").authorize(request);
            fail("marta can not access any of the private resources");
        } catch (RuntimeException expected) {
            assertEquals(403, HttpResponseException.class.cast(expected.getCause()).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected.getCause()).toString().contains("access_denied"));
        }

        // the private resource owned by kolo now has two scopes associated with it
        userResource.addScope("create", "read");
        authorization.resources().resource(userResource.getId()).update(userResource);

        // the private resource owned by no one also has two scopes associated with it
        typedResource.addScope("create", "read");
        authorization.resources().resource(typedResource.getId()).update(typedResource);

        // we create a scope permission that stands on its own and is associated with the create scope + "only public" policy
        // the type permission is associated with "only owner" + "only public" in affirmative mode
        ScopePermissionRepresentation createPermission = new ScopePermissionRepresentation();
        createPermission.setName(KeycloakModelUtils.generateId());
        createPermission.addScope("create");
        createPermission.addPolicy(onlyPublicResourcesPolicy.getName());

        authorization.permissions().scope().create(createPermission).close();

        // Marta still won't be able to access these resources as neither are public and neither are owned by marta
        try {
            authzClient.authorization("marta", "password").authorize(request);
            fail("marta can not access any of the private resources");
        } catch (RuntimeException expected) {
            assertEquals(403, HttpResponseException.class.cast(expected.getCause()).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected.getCause()).toString().contains("access_denied"));
        }

        // kolo will have access to the read scope on the typed resource because of the "only public + only owner" policy
        // the scope permission denies the access to "create" scope because the resource isn't public
        // TODO technically this might be a regression the same as other override behaviors
        response = authzClient.authorization("kolo", "password").authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertThat(userResource.getName(), Matchers.equalTo(grantedPermission.getResourceName()));
            assertThat(grantedPermission.getScopes(), Matchers.hasItem("read"));
        }

        // remove the private visibility from the resource owned by no one
        typedResource.setAttributes(new HashMap<>());

        authorization.resources().resource(typedResource.getId()).update(typedResource);

        // marta can now access that resource on the create scope and the update scope (because the type policy allows public while the scope policy only grants for one scope)
        // Marta still can't access the resource not owned by them as it is not public
        response = authzClient.authorization("marta", "password").authorize(request);
        assertNotNull(response.getToken());
        permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertThat(permissions.size(), Matchers.is(1));
        for (Permission grantedPermission : permissions) {
            assertThat(grantedPermission.getResourceName(), Matchers.is(typedResource.getName()));
            assertThat(grantedPermission.getScopes(), Matchers.containsInAnyOrder("create", "read"));
        }
    }

    @Test
    public void testOfflineRequestingPartyToken() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();

        JSPolicyRepresentation policy = new JSPolicyRepresentation();

        policy.setName(KeycloakModelUtils.generateId());
        policy.setType("script-scripts/default-policy.js");

        authorization.policies().js().create(policy).close();

        ResourceRepresentation resource = new ResourceRepresentation();

        resource.setName("Sensors");
        resource.addScope("sensors:view", "sensors:update", "sensors:delete");

        try (Response response = authorization.resources().create(resource)) {
            resource = response.readEntity(ResourceRepresentation.class);
        }

        ScopePermissionRepresentation permission = new ScopePermissionRepresentation();

        permission.setName("View Sensor");
        permission.addScope("sensors:view");
        permission.addPolicy(policy.getName());

        authorization.permissions().scope().create(permission).close();

        String accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).scope("offline_access").doGrantAccessTokenRequest("secret", "offlineuser", "password").getAccessToken();
        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);
        AccessTokenResponse response = authzClient.authorization(accessToken).authorize();
        assertNotNull(response.getToken());

        controller.stop(suiteContext.getAuthServerInfo().getQualifier());
        controller.start(suiteContext.getAuthServerInfo().getQualifier());
        reconnectAdminClient();
        configureSectorIdentifierRedirectUris();

        TokenIntrospectionResponse introspectionResponse = authzClient.protection().introspectRequestingPartyToken(response.getToken());

        assertTrue(introspectionResponse.getActive());
        assertFalse(introspectionResponse.getPermissions().isEmpty());

        response = authzClient.authorization(accessToken).authorize();
        assertNotNull(response.getToken());
    }

    @Test
    public void testProcessMappersForTargetAudience() throws Exception {
        ClientResource publicClient = getClient(getRealm(), PUBLIC_TEST_CLIENT);

        ProtocolMapperRepresentation customClaimMapper = new ProtocolMapperRepresentation();

        customClaimMapper.setName("custom_claim");
        customClaimMapper.setProtocolMapper(HardcodedClaim.PROVIDER_ID);
        customClaimMapper.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        Map<String, String> config = new HashMap<>();
        config.put(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME, "custom_claim");
        config.put(HardcodedClaim.CLAIM_VALUE, PUBLIC_TEST_CLIENT);
        config.put(OIDCAttributeMapperHelper.INCLUDE_IN_ACCESS_TOKEN, "true");
        customClaimMapper.setConfig(config);

        publicClient.getProtocolMappers().createMapper(customClaimMapper);

        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);

        config.put(HardcodedClaim.CLAIM_VALUE, RESOURCE_SERVER_TEST);

        client.getProtocolMappers().createMapper(customClaimMapper);

        AuthorizationResource authorization = client.authorization();

        JSPolicyRepresentation policy = new JSPolicyRepresentation();

        policy.setName(KeycloakModelUtils.generateId());
        policy.setType("script-scripts/default-policy.js");

        authorization.policies().js().create(policy).close();

        ResourceRepresentation resource = new ResourceRepresentation();

        resource.setName("Sensors");

        try (Response response = authorization.resources().create(resource)) {
            resource = response.readEntity(ResourceRepresentation.class);
        }

        ResourcePermissionRepresentation permission = new ResourcePermissionRepresentation();

        permission.setName("View Sensor");
        permission.addResource(resource.getName());
        permission.addPolicy(policy.getName());

        authorization.permissions().resource().create(permission).close();

        oauth.realm("authz-test");
        oauth.clientId(PUBLIC_TEST_CLIENT);
        oauth.doLogin("marta", "password");

        // Token request
        String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);
        OAuthClient.AccessTokenResponse response = oauth.doAccessTokenRequest(code, null);
        AccessToken token = toAccessToken(response.getAccessToken());

        assertEquals(PUBLIC_TEST_CLIENT, token.getOtherClaims().get("custom_claim"));

        AuthorizationRequest request = new AuthorizationRequest();

        request.addPermission("Sensors");

        AuthorizationResponse authorizationResponse = getAuthzClient(AUTHZ_CLIENT_CONFIG).authorization(response.getAccessToken()).authorize(request);
        token = toAccessToken(authorizationResponse.getToken());
        assertEquals(RESOURCE_SERVER_TEST, token.getOtherClaims().get("custom_claim"));
        assertEquals(PUBLIC_TEST_CLIENT, token.getIssuedFor());

        authorizationResponse = getAuthzClient(AUTHZ_CLIENT_CONFIG).authorization(response.getAccessToken()).authorize(request);
        token = toAccessToken(authorizationResponse.getToken());
        assertEquals(RESOURCE_SERVER_TEST, token.getOtherClaims().get("custom_claim"));
        assertEquals(PUBLIC_TEST_CLIENT, token.getIssuedFor());
    }

    @Test
    public void testRefreshTokenFromClientOtherThanAudience() throws Exception {
        oauth.realm("authz-test");
        oauth.clientId(PUBLIC_TEST_CLIENT);
        oauth.doLogin("marta", "password");
        String code = oauth.getCurrentQuery().get(OAuth2Constants.CODE);
        OAuthClient.AccessTokenResponse accessTokenResponse = oauth.doAccessTokenRequest(code, null);
        assertNotNull(accessTokenResponse.getAccessToken());
        assertNotNull(accessTokenResponse.getRefreshToken());

        AuthorizationRequest request = new AuthorizationRequest();
        request.setAudience(RESOURCE_SERVER_TEST);
        AuthorizationResponse authorizationResponse = getAuthzClient(PUBLIC_TEST_CLIENT_CONFIG).authorization(accessTokenResponse.getAccessToken()).authorize(request);
        AccessToken token = toAccessToken(authorizationResponse.getToken());
        assertEquals(PUBLIC_TEST_CLIENT, token.getIssuedFor());
        assertEquals(RESOURCE_SERVER_TEST, token.getAudience()[0]);
        assertFalse(token.getAuthorization().getPermissions().isEmpty());

        accessTokenResponse = oauth.doRefreshTokenRequest(authorizationResponse.getRefreshToken(), null);
        assertNotNull(accessTokenResponse.getAccessToken());
        assertNotNull(accessTokenResponse.getRefreshToken());
        token = toAccessToken(authorizationResponse.getToken());
        assertEquals(PUBLIC_TEST_CLIENT, token.getIssuedFor());
        assertFalse(token.getAuthorization().getPermissions().isEmpty());
    }

    @Test
    public void testUsingExpiredToken() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();

        JSPolicyRepresentation policy = new JSPolicyRepresentation();

        policy.setName(KeycloakModelUtils.generateId());
        policy.setType("script-scripts/default-policy.js");

        authorization.policies().js().create(policy).close();

        ResourceRepresentation resource = new ResourceRepresentation();

        resource.setName("Sensors");

        try (Response response = authorization.resources().create(resource)) {
            resource = response.readEntity(ResourceRepresentation.class);
        }

        ResourcePermissionRepresentation permission = new ResourcePermissionRepresentation();

        permission.setName("View Sensor");
        permission.addPolicy(policy.getName());

        authorization.permissions().resource().create(permission).close();

        String accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "marta", "password").getAccessToken();
        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);
        AccessTokenResponse response = authzClient.authorization(accessToken).authorize();
        assertNotNull(response.getToken());
        
        getRealm().logoutAll();

        AuthorizationRequest request = new AuthorizationRequest();

        request.addPermission("Sensors");
        request.setSubjectToken(accessToken);

        try {
            authzClient.authorization().authorize(request);
            fail("should fail, session invalidated");
        } catch (Exception e) {
            Throwable expected = e.getCause();
            assertEquals(400, HttpResponseException.class.cast(expected).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected).toString().contains("unauthorized_client"));
        }
    }

    @Test
    public void testInvalidTokenSignature() throws Exception {
        RealmEventsConfigRepresentation eventConfig = getRealm().getRealmEventsConfig();
        
        eventConfig.setEventsEnabled(true);
        eventConfig.setEnabledEventTypes(Arrays.asList(EventType.PERMISSION_TOKEN_ERROR.name()));
        
        getRealm().updateRealmEventsConfig(eventConfig);
        
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();

        JSPolicyRepresentation policy = new JSPolicyRepresentation();

        policy.setName(KeycloakModelUtils.generateId());
        policy.setType("script-scripts/default-policy.js");

        authorization.policies().js().create(policy).close();

        ResourceRepresentation resource = new ResourceRepresentation();

        resource.setName("Sensors");

        try (Response response = authorization.resources().create(resource)) {
            response.readEntity(ResourceRepresentation.class);
        }

        ResourcePermissionRepresentation permission = new ResourcePermissionRepresentation();

        permission.setName("View Sensor");
        permission.addPolicy(policy.getName());

        authorization.permissions().resource().create(permission).close();

        String accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "marta", "password").getAccessToken();
        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);
        AuthorizationRequest request = new AuthorizationRequest();

        request.addPermission("Sensors");
        request.setSubjectToken(accessToken + "i");

        try {
            authzClient.authorization().authorize(request);
            fail("should fail, session invalidated");
        } catch (Exception e) {
            Throwable expected = e.getCause();
            assertEquals(400, HttpResponseException.class.cast(expected).getStatusCode());
            assertTrue(HttpResponseException.class.cast(expected).toString().contains("unauthorized_client"));
        }

        List<EventRepresentation> events = getRealm()
                .getEvents(Arrays.asList(EventType.PERMISSION_TOKEN_ERROR.name()), null, null, null, null, null, null, null);
        assertEquals(1, events.size());
    }

    @Test
    public void testDenyScopeNotManagedByScopePolicy() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();

        // create a JS policy that always grants tied to no resource, type, or scope
        JSPolicyRepresentation policy = new JSPolicyRepresentation();
        policy.setName(KeycloakModelUtils.generateId());
        policy.setType("script-scripts/default-policy.js");

        authorization.policies().js().create(policy).close();

        // createa a resource with 3 scopes
        ResourceRepresentation resource = new ResourceRepresentation();
        resource.setName(KeycloakModelUtils.generateId());
        resource.addScope("sensors:view", "sensors:update", "sensors:delete");

        try (Response response = authorization.resources().create(resource)) {
            resource = response.readEntity(ResourceRepresentation.class);
        }

        // create a scope permission that's tied to the resource and one scope that has the JS policy as dependant
        ScopePermissionRepresentation permission = new ScopePermissionRepresentation();
        permission.setName(KeycloakModelUtils.generateId());
        permission.addResource(resource.getId());
        permission.addScope("sensors:view");
        permission.addPolicy(policy.getName());

        authorization.permissions().scope().create(permission).close();

        String accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", "kolo", "password").getAccessToken();
        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);

        AuthorizationRequest request = new AuthorizationRequest();
        request.addPermission(resource.getId(), "sensors:view");

        AuthorizationResponse response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        Collection<Permission> permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(resource.getId(), grantedPermission.getResourceId());
            assertEquals(1, grantedPermission.getScopes().size());
            assertThat(grantedPermission.getScopes(), hasItem("sensors:view"));
        }

        request = new AuthorizationRequest();
        request.addPermission(resource.getId(), "sensors:update");
        this.expectedException.expect(AuthorizationDeniedException.class);
        this.expectedException.expectCause(Matchers.allOf(Matchers.instanceOf(HttpResponseException.class), Matchers.hasProperty("statusCode", Matchers.is(403))));
        this.expectedException.reportMissingExceptionWithMessage("should fail, session invalidated");

        response = authzClient.authorization(accessToken).authorize(request);
    }

    @Test
    public void testPermissionsAcrossResourceServers() throws Exception {
        String rsAId;
        try (Response response = getRealm().clients().create(ClientBuilder.create().clientId("rs-a").secret("secret").serviceAccount().authorizationServicesEnabled(true).build())) {
            rsAId = ApiUtil.getCreatedId(response);
        }
        String rsBId;
        try (Response response = getRealm().clients().create(ClientBuilder.create().clientId("rs-b").secret("secret").serviceAccount().authorizationServicesEnabled(true).build())) {
            rsBId = ApiUtil.getCreatedId(response);
        }
        ClientResource rsB = getRealm().clients().get(rsBId);

        rsB.authorization().resources().create(new ResourceRepresentation("Resource A"));

        JSPolicyRepresentation grantPolicy = new JSPolicyRepresentation();

        grantPolicy.setName("Grant Policy");
        grantPolicy.setType("script-scripts/default-policy.js");

        rsB.authorization().policies().js().create(grantPolicy);

        ResourcePermissionRepresentation permission = new ResourcePermissionRepresentation();

        permission.setName("Resource A Permission");
        permission.addResource("Resource A");
        permission.addPolicy(grantPolicy.getName());

        rsB.authorization().permissions().resource().create(permission);

        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);
        Configuration config = authzClient.getConfiguration();

        config.setResource("rs-a");

        authzClient = AuthzClient.create(config);
        AccessTokenResponse accessTokenResponse = authzClient.obtainAccessToken();
        AccessToken accessToken = toAccessToken(accessTokenResponse.getToken());

        config.setResource("rs-b");

        AuthorizationRequest request = new AuthorizationRequest();

        request.addPermission("Resource A");

        AuthorizationResponse response = authzClient.authorization(accessTokenResponse.getToken()).authorize(request);

        assertNotNull(response.getToken());
        Collection<Permission> permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());
        assertEquals("Resource A", permissions.iterator().next().getResourceName());
    }

    @Test
    public void testClientToClientPermissionRequest() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();

        JSPolicyRepresentation policy = new JSPolicyRepresentation();

        policy.setName(KeycloakModelUtils.generateId());
        policy.setType("script-scripts/default-policy.js");

        authorization.policies().js().create(policy).close();

        ResourceRepresentation resource = new ResourceRepresentation();

        resource.setName("Sensors");

        try (Response response = authorization.resources().create(resource)) {
            response.readEntity(ResourceRepresentation.class);
        }

        ResourcePermissionRepresentation permission = new ResourcePermissionRepresentation();

        permission.setName("View Sensor");
        permission.addPolicy(policy.getName());

        authorization.permissions().resource().create(permission).close();

        ClientRepresentation otherClient = new ClientRepresentation();

        otherClient.setClientId("serviceB");
        otherClient.setServiceAccountsEnabled(true);
        otherClient.setSecret("secret");
        otherClient.setPublicClient(false);

        getRealm().clients().create(otherClient);

        Map<String, Object> credentials = new HashMap<>();

        credentials.put("secret", "secret");

        AuthzClient authzClient = AuthzClient
                .create(new Configuration(suiteContext.getAuthServerInfo().getContextRoot().toString() + "/auth",
                        getRealm().toRepresentation().getRealm(), otherClient.getClientId(),
                        credentials, getAuthzClient(AUTHZ_CLIENT_CONFIG).getConfiguration().getHttpClient()));

        AuthorizationRequest request = new AuthorizationRequest();

        request.setAudience(RESOURCE_SERVER_TEST);

        AuthorizationResponse response = authzClient.authorization().authorize(request);

        assertNotNull(response.getToken());
        // Refresh token should not be present
        assertNull(response.getRefreshToken());
    }

    @Test
    public void testPermissionOrder() throws Exception {
        ClientResource client = getClient(getRealm(), RESOURCE_SERVER_TEST);
        AuthorizationResource authorization = client.authorization();

        // create a js policy that grants outright
        JSPolicyRepresentation policy = new JSPolicyRepresentation();
        policy.setName(KeycloakModelUtils.generateId());
        policy.setType("script-scripts/default-policy.js");

        authorization.policies().js().create(policy).close();

        // create a resource with the read scope
        ResourceRepresentation resource = new ResourceRepresentation();
        resource.setName("my_resource");
        resource.addScope("entity:read");

        try (Response response = authorization.resources().create(resource)) {
            resource = response.readEntity(ResourceRepresentation.class);
        }

        // create a scope for feature:access
        ScopeRepresentation featureAccessScope = new ScopeRepresentation("feature:access");
        authorization.scopes().create(featureAccessScope);

        // create a resource permission with the always grant policy tied to the resource
        ResourcePermissionRepresentation permission = new ResourcePermissionRepresentation();
        permission.setName(KeycloakModelUtils.generateId());
        permission.addPolicy(policy.getName());
        permission.addResource(resource.getId());

        authorization.permissions().resource().create(permission).close();

        // create a scope policy tied to the feature:access scope with the "always grant" policy
        ScopePermissionRepresentation scopePermission = new ScopePermissionRepresentation();
        scopePermission.setName(KeycloakModelUtils.generateId());
        scopePermission.addPolicy(policy.getName());
        scopePermission.addScope(featureAccessScope.getName());

        authorization.permissions().scope().create(scopePermission).close();

        // request access to both scopes
        AuthorizationRequest request = new AuthorizationRequest();
        request.addPermission(null, "entity:read");
        request.addPermission(null, "feature:access");

        AuthzClient authzClient = getAuthzClient(AUTHZ_CLIENT_CONFIG);

        AuthorizationResponse response = authzClient.authorization().authorize(request);
        AccessToken token = toAccessToken(response.getToken());
        Authorization result = token.getAuthorization();

        // we should have access to both scopes due to permissions
        assertEquals(2, result.getPermissions().size());
        assertTrue(result.getPermissions().stream().anyMatch(p ->
                p.getResourceId() == null && p.getScopes().contains(featureAccessScope.getName())));
        String resourceId = resource.getId();
        assertTrue(result.getPermissions().stream().anyMatch(p ->
                p.getResourceId() != null && p.getResourceId().equals(resourceId) && p
                .getScopes().contains("entity:read")));

        request = new AuthorizationRequest();

        request.addPermission(null, "feature:access");
        request.addPermission(null, "entity:read");

        response = authzClient.authorization().authorize(request);
        token = toAccessToken(response.getToken());
        result = token.getAuthorization();

        assertEquals(2, result.getPermissions().size());
        assertTrue(result.getPermissions().stream().anyMatch(p ->
                p.getResourceId() == null && p.getScopes().contains(featureAccessScope.getName())));
        assertTrue(result.getPermissions().stream().anyMatch(p ->
                p.getResourceId() != null && p.getResourceId().equals(resourceId) && p
                        .getScopes().contains("entity:read")));
    }

    private void testRptRequestWithResourceName(String configFile) {
        Metadata metadata = new Metadata();

        metadata.setIncludeResourceName(true);

        assertResponse(metadata, () -> getAuthzClient(configFile).authorization("marta", "password").authorize());

        AuthorizationRequest request = new AuthorizationRequest();

        request.setMetadata(metadata);
        request.addPermission("Resource 13");

        assertResponse(metadata, () -> getAuthzClient(configFile).authorization("marta", "password").authorize(request));

        request.setMetadata(null);

        assertResponse(metadata, () -> getAuthzClient(configFile).authorization("marta", "password").authorize(request));
    }

    private void testResourceServerAsAudience(String testClientId, String resourceServerClientId, String configFile) throws Exception {
        AuthorizationRequest request = new AuthorizationRequest();

        request.addPermission("Resource 1");

        String accessToken = new OAuthClient().realm("authz-test").clientId(testClientId).doGrantAccessTokenRequest("secret", "marta", "password").getAccessToken();
        AuthorizationResponse response = getAuthzClient(configFile).authorization(accessToken).authorize(request);
        AccessToken rpt = toAccessToken(response.getToken());

        assertEquals(resourceServerClientId, rpt.getAudience()[0]);
    }

    private boolean hasPermission(String userName, String password, String resourceId, String... scopeIds) throws Exception {
        String accessToken = new OAuthClient().realm("authz-test").clientId(RESOURCE_SERVER_TEST).doGrantAccessTokenRequest("secret", userName, password).getAccessToken();
        AuthorizationResponse response = getAuthzClient(AUTHZ_CLIENT_CONFIG).authorization(accessToken).authorize(new AuthorizationRequest());
        AccessToken rpt = toAccessToken(response.getToken());
        Authorization authz = rpt.getAuthorization();
        Collection<Permission> permissions = authz.getPermissions();

        assertNotNull(permissions);
        assertFalse(permissions.isEmpty());

        for (Permission grantedPermission : permissions) {
            if (grantedPermission.getResourceId().equals(resourceId)) {
                return scopeIds == null || scopeIds.length == 0 || grantedPermission.getScopes().containsAll(Arrays.asList(scopeIds));
            }
        }

        return false;
    }

    private boolean hasPermission(String userName, String password, String resourceId) throws Exception {
        return hasPermission(userName, password, resourceId, null);
    }

    private void assertResponse(Metadata metadata, Supplier<AuthorizationResponse> responseSupplier) {
        AccessToken.Authorization authorization = toAccessToken(responseSupplier.get().getToken()).getAuthorization();

        Collection<Permission> permissions = authorization.getPermissions();

        assertNotNull(permissions);
        assertFalse(permissions.isEmpty());

        for (Permission permission : permissions) {
            if (metadata.getIncludeResourceName()) {
                assertNotNull(permission.getResourceName());
            } else {
                assertNull(permission.getResourceName());
            }
        }
    }

    private RealmResource getRealm() throws Exception {
        return adminClient.realm("authz-test");
    }

    private ClientResource getClient(RealmResource realm, String clientId) {
        ClientsResource clients = realm.clients();
        return clients.findByClientId(clientId).stream().map(representation -> clients.get(representation.getId())).findFirst().orElseThrow(() -> new RuntimeException("Expected client [resource-server-test]"));
    }

    private AuthzClient getAuthzClient(String configFile) {
        if (authzClient == null) {
            Configuration configuration;
            try {
                configuration = JsonSerialization.readValue(httpsAwareConfigurationStream(getClass().getResourceAsStream("/authorization-test/" + configFile)), Configuration.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read configuration", e);
            }
            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
            connectionManager.setValidateAfterInactivity(10);
            connectionManager.setMaxTotal(10);
            HttpClient client = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();
            authzClient = AuthzClient.create(new Configuration(configuration.getAuthServerUrl(), configuration.getRealm(), configuration.getResource(), configuration.getCredentials(), client));
        }

        return authzClient;
    }

    private void configureAuthorization(String clientId) throws Exception {
        ClientResource client = getClient(getRealm(), clientId);
        AuthorizationResource authorization = client.authorization();

        JSPolicyRepresentation policy = new JSPolicyRepresentation();

        policy.setName("Default Policy");
        policy.setType("script-scripts/default-policy.js");

        authorization.policies().js().create(policy).close();

        for (int i = 1; i <= 20; i++) {
            ResourceRepresentation resource = new ResourceRepresentation("Resource " + i);

            authorization.resources().create(resource).close();

            ResourcePermissionRepresentation permission = new ResourcePermissionRepresentation();

            permission.setName(resource.getName() + " Permission");
            permission.addResource(resource.getName());
            permission.addPolicy(policy.getName());

            authorization.permissions().resource().create(permission).close();
        }
    }

    private void removeAuthorization(String clientId) throws Exception {
        ClientResource client = getClient(getRealm(), clientId);
        ClientRepresentation representation = client.toRepresentation();

        representation.setAuthorizationServicesEnabled(false);

        client.update(representation);

        representation.setAuthorizationServicesEnabled(true);

        client.update(representation);
    }
    
    private void assertPermissions(AuthzClient authzClient, String accessToken, AuthorizationRequest request, ResourceRepresentation resource, String... expectedScopes) {
        AuthorizationResponse response = authzClient.authorization(accessToken).authorize(request);
        assertNotNull(response.getToken());
        Collection<Permission> permissions = toAccessToken(response.getToken()).getAuthorization().getPermissions();
        assertEquals(1, permissions.size());

        for (Permission grantedPermission : permissions) {
            assertEquals(resource.getId(), grantedPermission.getResourceId());
            assertEquals(expectedScopes.length, grantedPermission.getScopes().size());
            assertTrue(grantedPermission.getScopes().containsAll(Arrays.asList(expectedScopes)));
        }
    }
}
