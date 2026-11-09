const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
test('concurrent API requests share refresh and use renewed token', async () => {
 const expired = 'x.' + Buffer.from(JSON.stringify({exp:1})).toString('base64') + '.x';
 let auth = {schema:{flows:{authorizationCode:{tokenUrl:'https://issuer/token'}}},token:{access_token:expired,refresh_token:'refresh'}};
 let refreshes = 0, options;
 const ui={authSelectors:{authorized:()=>({get:()=>({toJS:()=>auth}),getIn:()=>auth.token.access_token})},
 authActions:{authorizeOauth2:result=>{auth={...auth,token:result.token}},logout:()=>{}},initOAuth:()=>{}};
 const context={window:{},location:{origin:'http://localhost:8080'},URL,URLSearchParams,Date,JSON,atob,
 fetch:async url=>{if(url==='/docs/config') return {ok:true,json:async()=>({clientId:'client'})};refreshes++; await new Promise(r=>setTimeout(r,5));return {ok:true,json:async()=>({access_token:'renewed',refresh_token:'rotated'})}},
 SwaggerUIBundle:config=>{options=config;return ui}};
 vm.runInNewContext(fs.readFileSync('src/main/resources/static/docs/login.js','utf8'),context);
 await new Promise(r=>setTimeout(r,0));
 const requests=[{url:'/api/v1/users',headers:{}},{url:'/api/v1/users/me',headers:{}}];
 await Promise.all(requests.map(r=>options.requestInterceptor(r)));
 assert.equal(refreshes,1);
 assert.equal(auth.token.refresh_token,'rotated');
 requests.forEach(r=>assert.equal(r.headers.Authorization,'Bearer renewed'));
});
