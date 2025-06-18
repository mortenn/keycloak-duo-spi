package com.mulesoft.keycloak.auth.spi.duo;

import com.duosecurity.duoweb.DuoWebException;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.UserModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static com.mulesoft.keycloak.auth.spi.duo.DuoMfaAuthenticatorFactory.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DuoMfaAuthenticatorTest {

    private DuoMfaAuthenticator newAuthenticator() {
        return new DuoMfaAuthenticator(duoWebFacadeMock);
    }

    @Mock
    DuoWebFacade duoWebFacadeMock;

    private AuthenticatorConfigModel mockConfig() {
        Map<String, String> m = new HashMap<>();
        m.put(PROP_IKEY, "XXXXXXXXXXXXXXXXXXXX");
        m.put(PROP_SKEY, "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        m.put(PROP_AKEY, "yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy");
        m.put(PROP_APIHOST, "api-99999999.duosecurity.com");

        AuthenticatorConfigModel config = mock(AuthenticatorConfigModel.class);
        doReturn(m).when(config).getConfig();
        return config;
    }

    private UserModel mockUser() {
        UserModel user = mock(UserModel.class);
        doReturn("username").when(user).getUsername();
        return user;
    }

    @Test
    public void testAction_successfulVerification() throws Exception {
        DuoMfaAuthenticator auth = newAuthenticator();

        AuthenticationFlowContext context = mock(AuthenticationFlowContext.class);
        UserModel user = mockUser();

        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        formData.add("sig_response", "dummy-sig-response");

        HttpRequest request = mock(HttpRequest.class);
        doReturn(formData).when(request).getDecodedFormParameters();

        doReturn(mockConfig()).when(context).getAuthenticatorConfig();
        doReturn(user).when(context).getUser();
        doReturn(request).when(context).getHttpRequest();
        doReturn("username").when(duoWebFacadeMock).verifyResponse(anyString(), anyString(), anyString(), anyString());

        auth.action(context);

        verify(context).success();
    }

    @Test
    public void testAction_invalidVerification() throws DuoWebException, NoSuchAlgorithmException, IOException, InvalidKeyException {
        DuoMfaAuthenticator auth = newAuthenticator();

        AuthenticationFlowContext context = mock(AuthenticationFlowContext.class);
        LoginFormsProvider lfp = mock(LoginFormsProvider.class);
        UserModel user = mockUser();

        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        formData.add("sig_response", "invalid:response");

        HttpRequest request = mock(HttpRequest.class);
        doReturn(formData).when(request).getDecodedFormParameters();

        doReturn(mockConfig()).when(context).getAuthenticatorConfig();
        doReturn(user).when(context).getUser();
        doReturn(lfp).when(context).form();
        doReturn(request).when(context).getHttpRequest();
        doThrow(new DuoWebException("Invalid response")).when(duoWebFacadeMock)
                .verifyResponse(anyString(), anyString(), anyString(), anyString());

        auth.action(context);

        verify(context).failureChallenge(eq(org.keycloak.authentication.AuthenticationFlowError.INVALID_CREDENTIALS), any());
    }
}
