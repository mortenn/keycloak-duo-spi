package com.mulesoft.keycloak.auth.spi.duo;

import com.duosecurity.duoweb.DuoWeb;
import com.duosecurity.duoweb.DuoWebException;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class DuoWebFacade
{
    public String signRequest(String iKey, String sKey, String aKey, String username)
    {
        return DuoWeb.signRequest(iKey, sKey, aKey, username);
    }

    public String verifyResponse(String iKey, String sKey, String aKey, String sigResponse)
            throws DuoWebException, NoSuchAlgorithmException, IOException, InvalidKeyException
    {
        return DuoWeb.verifyResponse(iKey, sKey, aKey, sigResponse);
    }
}
