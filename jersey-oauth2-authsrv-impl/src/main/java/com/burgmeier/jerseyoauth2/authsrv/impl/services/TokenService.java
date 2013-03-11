package com.burgmeier.jerseyoauth2.authsrv.impl.services;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.amber.oauth2.as.request.OAuthTokenRequest;
import org.apache.amber.oauth2.as.response.OAuthASResponse;
import org.apache.amber.oauth2.as.response.OAuthASResponse.OAuthAuthorizationResponseBuilder;
import org.apache.amber.oauth2.as.response.OAuthASResponse.OAuthTokenResponseBuilder;
import org.apache.amber.oauth2.common.exception.OAuthProblemException;
import org.apache.amber.oauth2.common.exception.OAuthSystemException;
import org.apache.amber.oauth2.common.message.OAuthResponse;
import org.apache.amber.oauth2.common.message.types.GrantType;
import org.apache.amber.oauth2.common.message.types.ResponseType;

import com.burgmeier.jerseyoauth2.api.client.IAuthorizedClientApp;
import com.burgmeier.jerseyoauth2.api.token.IAccessTokenInfo;
import com.burgmeier.jerseyoauth2.api.token.InvalidTokenException;
import com.burgmeier.jerseyoauth2.authsrv.api.client.IClientService;
import com.burgmeier.jerseyoauth2.authsrv.api.client.IPendingClientToken;
import com.burgmeier.jerseyoauth2.authsrv.api.token.IAccessTokenStorageService;
import com.burgmeier.jerseyoauth2.authsrv.api.token.ITokenGenerator;
import com.burgmeier.jerseyoauth2.authsrv.api.token.ITokenService;
import com.burgmeier.jerseyoauth2.authsrv.api.token.TokenStorageException;
import com.google.inject.Inject;

public class TokenService implements ITokenService {

	private final IAccessTokenStorageService accessTokenService;
	private final IClientService clientService;
	private final ITokenGenerator tokenGenerator;
	
	@Inject
	public TokenService(final IAccessTokenStorageService accessTokenService, final IClientService clientService, final ITokenGenerator tokenGenerator)
	{
		this.accessTokenService = accessTokenService;
		this.clientService = clientService;
		this.tokenGenerator = tokenGenerator;
		
	}

	@Override
	public void handleRequest(HttpServletRequest request, HttpServletResponse response, OAuthTokenRequest oauthRequest)
			throws OAuthSystemException, IOException, OAuthProblemException {
		if (oauthRequest.getGrantType().equals(GrantType.REFRESH_TOKEN.toString())) {

			refreshToken(request, response, oauthRequest);

		} else if (oauthRequest.getGrantType().equals(GrantType.AUTHORIZATION_CODE.toString())) {

			IPendingClientToken pendingClientToken = clientService.findPendingClientToken(oauthRequest.getClientId(),
					oauthRequest.getClientSecret(), oauthRequest.getCode());
			if (pendingClientToken == null) {
				throw OAuthProblemException.error("unauthorized_client", "client not authorized");
			}
			
			issueNewToken(request, response, pendingClientToken.getAuthorizedClient(), ResponseType.CODE);
		}
	}

	@Override
	public void issueNewToken(HttpServletRequest request, HttpServletResponse response, IAuthorizedClientApp clientApp, ResponseType responseType)
			throws OAuthProblemException, OAuthSystemException, IOException {

		String accessToken = tokenGenerator.createAccessToken();
		String refreshToken = tokenGenerator.createRefreshToken();

		try {
			IAccessTokenInfo accessTokenInfo = accessTokenService.issueToken(accessToken, refreshToken,
					clientApp);

			sendTokenResponse(request, response, accessTokenInfo, responseType);
		} catch (TokenStorageException e) {
			throw OAuthProblemException.error("server_error", "Server error");
		}
	}	
	
	protected void refreshToken(HttpServletRequest request, HttpServletResponse response, OAuthTokenRequest oauthRequest)
			throws OAuthSystemException, IOException, OAuthProblemException {
		String refreshToken = oauthRequest.getRefreshToken();

		try {
			IAccessTokenInfo oldTokenInfo = accessTokenService.getTokenInfoByRefreshToken(refreshToken);

			String newAccessToken = tokenGenerator.createAccessToken();
			String newRefreshToken = tokenGenerator.createRefreshToken();

			IAccessTokenInfo accessTokenInfo = accessTokenService.refreshToken(
					oldTokenInfo.getAccessToken(), newAccessToken, newRefreshToken);

			sendTokenResponse(request, response, accessTokenInfo, ResponseType.CODE);
		} catch (InvalidTokenException e) {
			throw OAuthProblemException.error("token_invalid", "token is invalid");
		} catch (TokenStorageException e) {
			throw OAuthProblemException.error("server_error", "Server error");						
		}
	}

	@Override
	public void sendErrorResponse(HttpServletResponse response, OAuthProblemException ex) throws OAuthSystemException,
			IOException {
		OAuthResponse r = OAuthResponse.errorResponse(401).error(ex).buildJSONMessage();

		response.setStatus(r.getResponseStatus());

		PrintWriter pw = response.getWriter();
		pw.print(r.getBody());
		pw.flush();
		pw.close();
	}

	@Override
	public void sendTokenResponse(HttpServletRequest request, HttpServletResponse response, IAccessTokenInfo accessTokenInfo, ResponseType responseType)
			throws OAuthSystemException, IOException {
		
		if (responseType.equals(ResponseType.TOKEN))
		{
			OAuthAuthorizationResponseBuilder responseBuilder = OAuthASResponse.authorizationResponse(request, HttpServletResponse.SC_MOVED_TEMPORARILY)
					.setAccessToken(accessTokenInfo.getAccessToken())
					.location(accessTokenInfo.getClientApp().getCallbackUrl())
					.setExpiresIn(accessTokenInfo.getExpiresIn());
			OAuthResponse authResponse = responseBuilder.buildQueryMessage();
			
			response.setStatus(authResponse.getResponseStatus());
			response.sendRedirect(authResponse.getLocationUri());
		} else {
			OAuthTokenResponseBuilder responseBuilder = OAuthASResponse.tokenResponse(HttpServletResponse.SC_OK)
						.setAccessToken(accessTokenInfo.getAccessToken())
						.setExpiresIn(accessTokenInfo.getExpiresIn())
						.setRefreshToken(accessTokenInfo.getRefreshToken());
			OAuthResponse r = responseBuilder.buildJSONMessage();

			response.setStatus(r.getResponseStatus());
			PrintWriter pw = response.getWriter();
			pw.print(r.getBody());
			pw.flush();
			pw.close();
		}
	}

}
