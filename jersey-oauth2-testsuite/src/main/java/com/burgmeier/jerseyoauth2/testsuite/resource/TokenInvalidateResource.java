package com.burgmeier.jerseyoauth2.testsuite.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import com.burgmeier.jerseyoauth2.authsrv.api.token.IAccessTokenStorageService;
import com.google.inject.Inject;

@Path("/invalidateToken")
public class TokenInvalidateResource {

	private final IAccessTokenStorageService accessTokenService;

	@Inject
	public TokenInvalidateResource(IAccessTokenStorageService accessTokenService) {
		super();
		this.accessTokenService = accessTokenService;
	}

	@GET
	public String invalidateToken(@QueryParam("username") String username)
	{
		accessTokenService.invalidateTokensForUser(username);
		return "OK";
	}
}
