/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.authentication.authenticators.browser;

import com.webauthn4j.data.WebAuthnAuthenticationContext;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.util.exception.WebAuthnException;

import org.jboss.logging.Logger;
import org.keycloak.WebAuthnConstants;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.common.util.Base64Url;
import org.keycloak.common.util.UriUtils;
import org.keycloak.credential.WebAuthnCredentialModel;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.forms.login.freemarker.model.WebAuthnAuthenticatorsBean;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

public class WebAuthnAuthenticator implements Authenticator {

    private static final Logger logger = Logger.getLogger(WebAuthnAuthenticator.class);
    private KeycloakSession session;

    public WebAuthnAuthenticator(KeycloakSession session) {
        this.session = session;
    }

    public void authenticate(AuthenticationFlowContext context) {
        LoginFormsProvider form = context.form();
 
        Challenge challenge = new DefaultChallenge();
        String challengeValue = Base64Url.encode(challenge.getValue());
        context.getAuthenticationSession().setAuthNote(WebAuthnConstants.AUTH_CHALLENGE_NOTE, challengeValue);
        form.setAttribute(WebAuthnConstants.CHALLENGE, challengeValue);

        String rpId = context.getRealm().getWebAuthnPolicy().getRpId();
        if (rpId == null || rpId.isEmpty()) rpId =  context.getUriInfo().getBaseUri().getHost();
        form.setAttribute(WebAuthnConstants.RP_ID, rpId);

        UserModel user = context.getUser();
        boolean isUserIdentified = false;
        if (user != null) {
            // in 2 Factor Scenario where the user has already been identified
            WebAuthnAuthenticatorsBean authenticators = new WebAuthnAuthenticatorsBean(user);
            if (authenticators.getAuthenticators().isEmpty()) {
                setErrorResponse(context, ERR_NO_AUTHENTICATORS_REGISTERED, null);
                return;
            }
            isUserIdentified = true;
            form.setAttribute(WebAuthnConstants.ALLOWED_AUTHENTICATORS, authenticators);
        } else {
            // in ID-less & Password-less Scenario
            // NOP
        }
        form.setAttribute(WebAuthnConstants.IS_USER_IDENTIFIED, Boolean.toString(isUserIdentified));

        // read options from authenticator config
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        String userVerificationRequirement = config.getConfig().get(WebAuthnAuthenticatorFactory.USER_VERIFICATION_REQUIREMENT);
        if (WebAuthnConstants.OPTION_NOT_SPECIFIED.equals(userVerificationRequirement)) userVerificationRequirement = Constants.DEFAULT_WEBAUTHN_POLICY_NOT_SPECIFIED;
        form.setAttribute(WebAuthnConstants.USER_VERIFICATION, userVerificationRequirement);

        context.challenge(form.createForm("webauthn-authenticate.ftl"));
    }

    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> params = context.getHttpRequest().getDecodedFormParameters();

        // receive error from navigator.credentials.get()
        String errorMsgFromWebAuthnApi = params.getFirst(WebAuthnConstants.ERROR);
        if (errorMsgFromWebAuthnApi != null && !errorMsgFromWebAuthnApi.isEmpty()) {
            setErrorResponse(context, ERR_WEBAUTHN_API_GET, errorMsgFromWebAuthnApi);
            return;
        }

        String baseUrl = UriUtils.getOrigin(context.getUriInfo().getBaseUri());
        String rpId = context.getUriInfo().getBaseUri().getHost();

        Origin origin = new Origin(baseUrl);
        Challenge challenge = new DefaultChallenge(context.getAuthenticationSession().getAuthNote(WebAuthnConstants.AUTH_CHALLENGE_NOTE));
        ServerProperty server = new ServerProperty(origin, rpId, challenge, null);

        byte[] credentialId = Base64Url.decode(params.getFirst(WebAuthnConstants.CREDENTIAL_ID));
        byte[] clientDataJSON = Base64Url.decode(params.getFirst(WebAuthnConstants.CLIENT_DATA_JSON));
        byte[] authenticatorData = Base64Url.decode(params.getFirst(WebAuthnConstants.AUTHENTICATOR_DATA));
        byte[] signature = Base64Url.decode(params.getFirst(WebAuthnConstants.SIGNATURE));

        String userId = params.getFirst(WebAuthnConstants.USER_HANDLE);
        boolean isUVFlagChecked = false;
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        String userVerificationRequirement = config.getConfig().get(WebAuthnAuthenticatorFactory.USER_VERIFICATION_REQUIREMENT);
        if (WebAuthnConstants.OPTION_REQUIRED.equals(userVerificationRequirement)) isUVFlagChecked = true;

        // existing User Handle means that the authenticator used Resident Key supported public key credential
        if (userId == null || userId.isEmpty()) {
            // Resident Key not supported public key credential was used
            // so rely on the user that has already been authenticated
            userId = context.getUser().getId();
        } else {
            if (context.getUser() != null) {
                // Resident Key supported public key credential was used,
                // so need to confirm whether the already authenticated user is equals to one authenticated by the webauthn authenticator
                String firstAuthenticatedUserId = context.getUser().getId();
                if (firstAuthenticatedUserId != null && !firstAuthenticatedUserId.equals(userId)) {
                    context.getEvent()
                        .detail("first_authenticated_user_id", firstAuthenticatedUserId)
                        .detail("web_authn_authenticator_authenticated_user_id", userId);
                    setErrorResponse(context, ERR_DIFFERENT_USER_AUTHENTICATED, null);
                    return;
                }
            } else {
                // Resident Key supported public key credential was used,
                // and the user has not yet been identified
                // so rely on the user authenticated by the webauthn authenticator
                // NOP
            }
        }
        UserModel user = session.users().getUserById(userId, context.getRealm());
        WebAuthnAuthenticationContext authenticationContext = new WebAuthnAuthenticationContext(
                credentialId,
                clientDataJSON,
                authenticatorData,
                signature,
                server,
                isUVFlagChecked
        );

        WebAuthnCredentialModel cred = new WebAuthnCredentialModel();
        cred.setAuthenticationContext(authenticationContext);

        boolean result = false;
        try {
            result = session.userCredentialManager().isValid(context.getRealm(), user, cred);
        } catch (WebAuthnException wae) {
            setErrorResponse(context, ERR_WEBAUTHN_VERIFICATION_FAIL, wae.getMessage());
            return;
        }
        String encodedCredentialID = Base64Url.encode(credentialId);
        if (result) {
            String isUVChecked = Boolean.toString(isUVFlagChecked);
            logger.infov("WebAuthn Authentication successed. isUserVerificationChecked = {0}, PublicKeyCredentialID = {1}", isUVChecked, encodedCredentialID);
            context.setUser(user);
            context.getEvent()
                .detail("web_authn_authenticator_user_verification_checked", isUVChecked)
                .detail("public_key_credential_id", encodedCredentialID);
            context.success();
        } else {
            context.getEvent()
                .detail("web_authn_authenticated_user_id", userId)
                .detail("public_key_credential_id", encodedCredentialID);
            setErrorResponse(context, ERR_WEBAUTHN_AUTHENTICATED_USER_NOT_FOUND, null);
            context.cancelLogin();
        }
    }

    public boolean requiresUser() {
        return false;
    }

    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // NOP
    }

    public void close() {
        // NOP
    }

    private static final String ERR_LABEL = "web_authn_authentication_error";
    private static final String ERR_DETAIL_LABEL = "web_authn_authentication_error_detail";
    private static final String ERR_NO_AUTHENTICATORS_REGISTERED = "No WebAuthn Authenticator registered.";
    private static final String ERR_WEBAUTHN_API_GET = "Failed to authenticate by the WebAuthn Authenticator";
    private static final String ERR_DIFFERENT_USER_AUTHENTICATED = "First authenticated user is not the one authenticated by the WebAuthn authenticator.";
    private static final String ERR_WEBAUTHN_VERIFICATION_FAIL = "WetAuthn Authentication result is invalid.";
    private static final String ERR_WEBAUTHN_AUTHENTICATED_USER_NOT_FOUND = "Unknown user authenticated by the WebAuthen Authenticator";

    private void setErrorResponse(AuthenticationFlowContext context, final String errorCase, final String errorMessage) {
        Response errorResponse = null;
        switch (errorCase) {
        case ERR_NO_AUTHENTICATORS_REGISTERED:
            logger.warn(errorCase);
            context.getEvent()
                .detail(ERR_LABEL, errorCase)
                .error(Errors.INVALID_USER_CREDENTIALS);
            errorResponse = context.form()
                .setError(errorCase)
                .createErrorPage(Response.Status.BAD_REQUEST);
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS, errorResponse);
            break;
        case ERR_WEBAUTHN_API_GET:
            logger.warnv("error returned from navigator.credentials.get(). {0}", errorMessage);
            context.getEvent()
                .detail(ERR_LABEL, errorCase)
                .detail(ERR_DETAIL_LABEL, errorMessage)
                .error(Errors.NOT_ALLOWED);
            errorResponse = context.form()
                .setError(errorCase)
                .createErrorPage(Response.Status.BAD_REQUEST);
            context.failure(AuthenticationFlowError.INVALID_USER, errorResponse);
            break;
        case ERR_DIFFERENT_USER_AUTHENTICATED:
            logger.warn(errorCase);
            context.getEvent()
                .detail(ERR_LABEL, errorCase)
                .error(Errors.DIFFERENT_USER_AUTHENTICATED);
            errorResponse = context.form()
                .setError(errorCase)
                .createErrorPage(Response.Status.BAD_REQUEST);
            context.failure(AuthenticationFlowError.USER_CONFLICT, errorResponse);
            break;
        case ERR_WEBAUTHN_VERIFICATION_FAIL:
            logger.warnv("WebAuthn API .get() response validation failure. {0}", errorMessage);
            context.getEvent()
                .detail(ERR_LABEL, errorCase)
                .detail(ERR_DETAIL_LABEL, errorMessage)
                .error(Errors.INVALID_USER_CREDENTIALS);
            errorResponse = context.form()
                .setError(errorCase)
                .createErrorPage(Response.Status.BAD_REQUEST);
            context.failure(AuthenticationFlowError.INVALID_USER, errorResponse);
            break;
        case ERR_WEBAUTHN_AUTHENTICATED_USER_NOT_FOUND:
            logger.warn(errorCase);
            context.getEvent().detail(ERR_LABEL, errorCase);
            context.getEvent().error(Errors.USER_NOT_FOUND);
            break;
        default:
                // NOP
        }
    }
}
