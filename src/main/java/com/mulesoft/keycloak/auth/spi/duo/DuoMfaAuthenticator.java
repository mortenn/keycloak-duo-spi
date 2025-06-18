/*
Copyright 2018 MuleSoft, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.mulesoft.keycloak.auth.spi.duo;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import static com.mulesoft.keycloak.auth.spi.duo.DuoMfaAuthenticatorFactory.*;

public class DuoMfaAuthenticator implements Authenticator {

    private static final Logger logger = Logger.getLogger(DuoMfaAuthenticator.class);
    private final DuoWebFacade duoWeb;

    public DuoMfaAuthenticator()
    {
        this(new DuoWebFacade());
    }

    // For testing
    public DuoMfaAuthenticator(DuoWebFacade duoWeb)
    {
        this.duoWeb = duoWeb;
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No enrollment required within Keycloak
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        if (!isDuoConfigValid(context)) {
            logger.error("Duo MFA configuration is missing or invalid.");
            LoginFormsProvider form = context.form();
            form.setError("Duo MFA configuration error.");
            Response response = form.createErrorPage(Response.Status.INTERNAL_SERVER_ERROR);
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR, response);
            return;
        }

        String sigRequest = duoWeb.signRequest(
                duoIKey(context), duoSKey(context), duoAKey(context), context.getUser().getUsername());

        LoginFormsProvider form = context.form();
        form.setAttribute("sig_request", sigRequest);
        form.setAttribute("apihost", duoApiHost(context));
        form.addScript("https://api.duosecurity.com/frame/hosted/Duo-Web-v2.js");

        if (sigRequest.startsWith("ERR")) {
            logger.errorf("Duo signRequest failed: %s", sigRequest);
            form.setError("Duo signRequest failed: " + sigRequest);
        }

        context.challenge(form.createForm("duo-mfa.ftl"));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        if (formData.containsKey("cancel")) {
            logger.info("User cancelled Duo MFA.");
            context.resetFlow();
            return;
        }

        if (!formData.containsKey("sig_response")) {
            logger.warn("Missing sig_response from Duo.");
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    createDuoForm(context, "Missing sig_response from Duo."));
            return;
        }

        String sigResponse = formData.getFirst("sig_response");
        String authenticatedUsername;

        try {
            authenticatedUsername = duoWeb.verifyResponse(
                    duoIKey(context), duoSKey(context), duoAKey(context), sigResponse);
        } catch (Exception ex) {
            logger.error("Duo verification failed", ex);
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    createDuoForm(context, "Duo verification failed."));
            return;
        }

        String expectedUsername = context.getUser().getUsername();

        if (authenticatedUsername == null || !authenticatedUsername.equals(expectedUsername)) {
            logger.warnf("Duo returned mismatched username. Expected: %s, Got: %s", expectedUsername, authenticatedUsername);
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    createDuoForm(context, "Duo authentication mismatch."));
            return;
        }

        logger.debugf("Duo authentication succeeded for user: %s", expectedUsername);
        context.success();
    }

    @Override
    public void close() {}

    private Response createDuoForm(AuthenticationFlowContext context, String error) {
        String sigRequest = duoWeb.signRequest(
                duoIKey(context), duoSKey(context), duoAKey(context), context.getUser().getUsername());

        LoginFormsProvider form = context.form();
        form.setAttribute("sig_request", sigRequest);
        form.setAttribute("apihost", duoApiHost(context));
        form.addScript("https://api.duosecurity.com/frame/hosted/Duo-Web-v2.js");

        if (error != null) {
            form.setError(error);
        }

        return form.createForm("duo-mfa.ftl");
    }

    private boolean isDuoConfigValid(AuthenticationFlowContext context) {
        String iKey = duoIKey(context);
        String sKey = duoSKey(context);
        String aKey = duoAKey(context);
        String host = duoApiHost(context);

        return !iKey.isBlank() && !sKey.isBlank() && aKey.length() >= 40 && host.matches("^[a-zA-Z0-9.-]+$");
    }

    private String duoIKey(AuthenticationFlowContext context) {
        return getConfigValue(context, PROP_IKEY);
    }

    private String duoSKey(AuthenticationFlowContext context) {
        return getConfigValue(context, PROP_SKEY);
    }

    private String duoAKey(AuthenticationFlowContext context) {
        return getConfigValue(context, PROP_AKEY);
    }

    private String duoApiHost(AuthenticationFlowContext context) {
        return getConfigValue(context, PROP_APIHOST);
    }

    private String getConfigValue(AuthenticationFlowContext context, String key) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        return (config != null && config.getConfig() != null)
                ? String.valueOf(config.getConfig().getOrDefault(key, "")).trim()
                : "";
    }
}
