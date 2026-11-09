/* Tokens remain in Swagger's in-memory store; refresh is serialized before API requests. */
(async () => {
  const configResponse = await fetch('/docs/config');
  if (!configResponse.ok) throw new Error('Swagger configuration unavailable');
  const config = await configResponse.json();
  let refreshInFlight;
  const refresh = async () => {
    const authorized = ui.authSelectors.authorized().get('keycloak');
    if (!authorized) return;
    const auth = authorized.toJS();
    const token = auth.token;
    if (!token?.access_token) return;
    const part = token.access_token.split('.')[1];
    const claims = JSON.parse(atob(part.replace(/-/g, '+').replace(/_/g, '/')));
    if (claims.exp * 1000 > Date.now() + 30000) return;
    if (!token.refresh_token) throw new Error('Session expired. Authorize again.');
    const endpoint = auth.schema.flows.authorizationCode.tokenUrl;
    const response = await fetch(endpoint, {
      method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'},
      body: new URLSearchParams({grant_type: 'refresh_token', client_id: config.clientId,
        refresh_token: token.refresh_token})
    });
    if (!response.ok) {
      if (response.status === 400 || response.status === 401) ui.authActions.logout(['keycloak']);
      throw new Error('Unable to refresh session. Authorize again or retry.');
    }
    const next = await response.json();
    ui.authActions.authorizeOauth2({auth: {name: 'keycloak', schema: auth.schema,
      clientId: config.clientId}, token: {...token, ...next}});
  };
  const ui = window.ui = SwaggerUIBundle({
    url: '/v3/api-docs', dom_id: '#swagger-ui', persistAuthorization: false,
    oauth2RedirectUrl: location.origin + '/swagger-ui/oauth2-redirect.html',
    requestInterceptor: async request => {
      const target = new URL(request.url, location.origin);
      if (target.origin === location.origin && target.pathname.startsWith('/api/')) {
        if (!refreshInFlight) refreshInFlight = refresh().finally(() => {refreshInFlight = undefined;});
        await refreshInFlight;
        const token = ui.authSelectors.authorized().getIn(['keycloak', 'token', 'access_token']);
        if (token) request.headers.Authorization = 'Bearer ' + token;
      }
      return request;
    }
  });
  ui.initOAuth({clientId: config.clientId, scopes: 'openid', usePkceWithAuthorizationCodeGrant: true});
})();
