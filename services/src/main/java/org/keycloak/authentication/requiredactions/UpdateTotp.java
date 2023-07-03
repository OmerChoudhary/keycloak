/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.authentication.requiredactions;

import org.keycloak.Config;
import org.keycloak.authentication.CredentialRegistrator;
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.credential.CredentialModel;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.credential.OTPCredentialProvider;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.OTPPolicy;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.models.utils.CredentialValidation;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.models.utils.HmacOTP;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;
import org.keycloak.utils.CredentialHelper;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.utils.TotpUtils;

import java.util.stream.Stream;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class UpdateTotp implements RequiredActionProvider, RequiredActionFactory, CredentialRegistrator {

    public static final String MODE = "mode";
    public static final String TOTP_SECRET = "totpSecret";

    private static final String NULL_TOTP_SECRET = "null_totp_secret";

    private static final int HMAC_OTP_LENGTH = 20;

    @Override
    public InitiatedActionSupport initiatedActionSupport() {
        return InitiatedActionSupport.SUPPORTED;
    }
    
    @Override
    public void evaluateTriggers(RequiredActionContext context) {
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        if (context.getAuthenticationSession().getAuthNote(TOTP_SECRET) == null) {
            context.getAuthenticationSession().setAuthNote(TOTP_SECRET, HmacOTP.generateSecret(HMAC_OTP_LENGTH));
        }

        Response challenge = context.form()
                .setAttribute(MODE, context.getUriInfo().getQueryParameters().getFirst(MODE))
                .createResponse(UserModel.RequiredAction.CONFIGURE_TOTP);
        context.challenge(challenge);
    }

    @Override
    public void processAction(RequiredActionContext context) {
        EventBuilder event = context.getEvent();
        event.event(EventType.UPDATE_TOTP);

        Response challenge;

        String totpSecret = context.getAuthenticationSession().getAuthNote(TOTP_SECRET);
        if (totpSecret == null) {
            challenge = context.form()
                    .addError(new FormMessage(Validation.FIELD_OTP_CODE, NULL_TOTP_SECRET)).createResponse(UserModel.RequiredAction.CONFIGURE_TOTP);
            event.error(NULL_TOTP_SECRET);
            context.challenge(challenge);
            return;
        }

        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String challengeResponse = formData.getFirst("totp").trim();
        String userLabel = formData.getFirst("userLabel");
        String mode = formData.getFirst(MODE);

        OTPPolicy policy = context.getRealm().getOTPPolicy();
        OTPCredentialModel credentialModel = OTPCredentialModel.createFromPolicy(context.getRealm(), totpSecret, userLabel);
        if (Validation.isBlank(challengeResponse)) {
            challenge = context.form()
                    .setAttribute(MODE, mode)
                    .addError(new FormMessage(Validation.FIELD_OTP_CODE, Messages.MISSING_TOTP))
                    .createResponse(UserModel.RequiredAction.CONFIGURE_TOTP);
            event.error("blank_totp_code");
            context.challenge(challenge);
            return;
        }

        if (!validateOTPCredential(context, challengeResponse, credentialModel, policy)) {
            challenge = context.form()
                    .setAttribute(MODE, mode)
                    .addError(new FormMessage(Validation.FIELD_OTP_CODE, Messages.INVALID_TOTP))
                    .createResponse(UserModel.RequiredAction.CONFIGURE_TOTP);
            event.error("invalid_totp_code");
            context.challenge(challenge);
            return;
        }

        OTPCredentialProvider otpCredentialProvider = (OTPCredentialProvider) context.getSession().getProvider(CredentialProvider.class, "keycloak-otp");
        final Stream<CredentialModel> otpCredentials  = (otpCredentialProvider.isConfiguredFor(context.getRealm(), context.getUser()))
            ? context.getUser().credentialManager().getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE)
            : Stream.empty();
        if (otpCredentials.count() >= 1 && Validation.isBlank(userLabel)) {
            challenge = context.form()
                    .setAttribute(MODE, mode)
                    .addError(new FormMessage(Validation.FIELD_OTP_LABEL, Messages.MISSING_TOTP_DEVICE_NAME))
                    .createResponse(UserModel.RequiredAction.CONFIGURE_TOTP);
            event.error("missing_totp_device_name");
            context.challenge(challenge);
            return;
        }

        if (!CredentialHelper.createOTPCredential(context.getSession(), context.getRealm(), context.getUser(), challengeResponse, credentialModel)) {
            challenge = context.form()
                    .setAttribute(MODE, mode)
                    .addError(new FormMessage(Validation.FIELD_OTP_CODE, Messages.INVALID_TOTP))
                    .createResponse(UserModel.RequiredAction.CONFIGURE_TOTP);
            event.error("invalid_totp_code");
            context.challenge(challenge);
            return;
        }
        context.success();
    }


    // Use separate method, so it's possible to override in the custom provider
    protected boolean validateOTPCredential(RequiredActionContext context, String token, OTPCredentialModel credentialModel, OTPPolicy policy) {
        return CredentialValidation.validOTP(token, credentialModel, policy.getLookAheadWindow());
    }


    @Override
    public void close() {

    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public String getDisplayText() {
        return "Configure OTP";
    }


    @Override
    public String getId() {
        return UserModel.RequiredAction.CONFIGURE_TOTP.name();
    }

    @Override
    public boolean isOneTimeAction() {
        return true;
    }

}
