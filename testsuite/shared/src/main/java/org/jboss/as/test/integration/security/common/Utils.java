/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.security.common;

import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.x500.X500Principal;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolException;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.network.NetworkUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.wildfly.security.x500.cert.SelfSignedX509CertificateAndSigningKey;

/**
 * Common utilities for JBoss AS security tests.
 *
 * @author Jan Lanik
 * @author Josef Cacek
 */
public class Utils extends CoreUtils {

    private static final Logger LOGGER = Logger.getLogger(Utils.class);

    public static final String UTF_8 = "UTF-8";

    private static final char[] KEYSTORE_CREATION_PASSWORD = "123456".toCharArray();

    private static void createKeyStoreTrustStore(KeyStore keyStore, KeyStore trustStore, String DN, String alias) throws Exception {
        X500Principal principal = new X500Principal(DN);

        SelfSignedX509CertificateAndSigningKey selfSignedX509CertificateAndSigningKey = SelfSignedX509CertificateAndSigningKey.builder()
                .setKeyAlgorithmName("RSA")
                .setSignatureAlgorithmName("SHA256withRSA")
                .setDn(principal)
                .setKeySize(1024)
                .build();
        X509Certificate certificate = selfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();

        keyStore.setKeyEntry(alias, selfSignedX509CertificateAndSigningKey.getSigningKey(), KEYSTORE_CREATION_PASSWORD, new X509Certificate[]{certificate});
        if(trustStore != null) trustStore.setCertificateEntry(alias, certificate);
    }

    private static KeyStore loadKeyStore() throws Exception{
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        return ks;
    }

    private static void createTemporaryCertFile(X509Certificate cert, File outputFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputFile)){
            fos.write(cert.getTBSCertificate());
        }
    }

    private static void createTemporaryKeyStoreFile(KeyStore keyStore, File outputFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputFile)){
            keyStore.store(fos, KEYSTORE_CREATION_PASSWORD);
        }
    }

    private static void generateKeyMaterial(final File keyStoreDir) throws Exception {
        KeyStore clientKeyStore = loadKeyStore();
        KeyStore clientTrustStore = loadKeyStore();
        KeyStore serverKeyStore = loadKeyStore();
        KeyStore serverTrustStore = loadKeyStore();
        KeyStore untrustedKeyStore = loadKeyStore();

        createKeyStoreTrustStore(clientKeyStore, serverTrustStore, "CN=client", "cn=client");
        createKeyStoreTrustStore(serverKeyStore, clientTrustStore, "CN=server", "cn=server");
        createKeyStoreTrustStore(untrustedKeyStore, null, "CN=untrusted", "cn=untrusted");

        File clientCertFile = new File(keyStoreDir, "client.crt");
        File clientKeyFile = new File(keyStoreDir, "client.keystore");
        File clientTrustFile = new File(keyStoreDir, "client.truststore");
        File serverCertFile = new File(keyStoreDir, "server.crt");
        File serverKeyFile = new File(keyStoreDir, "server.keystore");
        File serverTrustFile = new File(keyStoreDir, "server.truststore");
        File untrustedCertFile = new File(keyStoreDir, "untrusted.crt");
        File untrustedKeyFile = new File(keyStoreDir, "untrusted.keystore");

        createTemporaryCertFile((X509Certificate) clientKeyStore.getCertificate("cn=client"), clientCertFile);
        createTemporaryCertFile((X509Certificate) serverKeyStore.getCertificate("cn=server"), serverCertFile);
        createTemporaryCertFile((X509Certificate) untrustedKeyStore.getCertificate("cn=untrusted"), untrustedCertFile);

        createTemporaryKeyStoreFile(clientKeyStore, clientKeyFile);
        createTemporaryKeyStoreFile(clientTrustStore, clientTrustFile);
        createTemporaryKeyStoreFile(serverKeyStore, serverKeyFile);
        createTemporaryKeyStoreFile(serverTrustStore, serverTrustFile);
        createTemporaryKeyStoreFile(untrustedKeyStore, untrustedKeyFile);
    }

    /** The REDIRECT_STRATEGY for Apache HTTP Client */
    public static final RedirectStrategy REDIRECT_STRATEGY = new DefaultRedirectStrategy() {

        @Override
        public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
            boolean isRedirect = false;
            try {
                isRedirect = super.isRedirected(request, response, context);
            } catch (ProtocolException e) {
                e.printStackTrace();
            }
            if (!isRedirect) {
                final int responseCode = response.getStatusLine().getStatusCode();
                isRedirect = (responseCode == 301 || responseCode == 302);
            }
            return isRedirect;
        }
    };

    public static String hash(String target, String algorithm, Coding coding) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance(algorithm);
        } catch (Exception e) {
            e.printStackTrace();
        }
        assert md != null;
        byte[] bytes = target.getBytes(StandardCharsets.UTF_8);
        byte[] byteHash = md.digest(bytes);

        String encodedHash;

        switch (coding) {
            case BASE_64:
                encodedHash = Base64.getEncoder().encodeToString(byteHash);
                break;
            case HEX:
                encodedHash = toHex(byteHash);
                break;
            default:
                throw new IllegalArgumentException("Unsuported coding:" + coding.name());
        }

        return encodedHash;
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            // top 4 bits
            char c = (char) ((b >> 4) & 0xf);
            if (c > 9)
                c = (char) ((c - 10) + 'a');
            else
                c = (char) (c + '0');
            sb.append(c);
            // bottom 4 bits
            c = (char) (b & 0xf);
            if (c > 9)
                c = (char) ((c - 10) + 'a');
            else
                c = (char) (c + '0');
            sb.append(c);
        }
        return sb.toString();
    }

    public static URL getResource(String name) {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        return tccl.getResource(name);
    }

    private static final long STOP_DELAY_DEFAULT = 0;

    /**
     * stops execution of the program indefinitely useful in testsuite debugging
     */
    public static void stop() {
        stop(STOP_DELAY_DEFAULT);
    }

    /**
     * stop test execution for a given time interval useful for debugging
     *
     * @param delay interval (milliseconds), if delay<=0, interval is considered to be infinite (Long.MAX_VALUE)
     */
    public static void stop(long delay) {
        long currentTime = System.currentTimeMillis();
        long remainingTime = 0 < delay ? currentTime + delay - System.currentTimeMillis() : Long.MAX_VALUE;
        while (remainingTime > 0) {
            try {
                Thread.sleep(remainingTime);
            } catch (InterruptedException ex) {
                remainingTime = currentTime + delay - System.currentTimeMillis();
            }
        }
    }

    public static void applyUpdates(final List<ModelNode> updates, final ModelControllerClient client) throws Exception {
        for (ModelNode update : updates) {
            applyUpdate(update, client);
        }
    }

    public static void applyUpdate(ModelNode update, final ModelControllerClient client) throws Exception {
        ModelNode result = client.execute(new OperationBuilder(update).build());
        if (LOGGER.isInfoEnabled()) {
            LOGGER.trace("Client update: " + update);
            LOGGER.trace("Client update result: " + result);
        }
        if (result.hasDefined("outcome") && "success".equals(result.get("outcome").asString())) {
            LOGGER.debug("Operation succeeded.");
        } else if (result.hasDefined("failure-description")) {
            throw new RuntimeException(result.get("failure-description").toString());
        } else {
            throw new RuntimeException("Operation not successful; outcome = " + result.get("outcome"));
        }
    }

    /**
     * Read the contents of an HttpResponse's entity and return it as a String. The content is converted using the character set
     * from the entity (if any), failing that, "ISO-8859-1" is used.
     *
     * @param response
     * @return
     * @throws IOException
     */
    public static String getContent(HttpResponse response) throws IOException {
        return EntityUtils.toString(response.getEntity());
    }

    /**
     * Makes HTTP call with FORM authentication.
     *
     * @param URL
     * @param user
     * @param pass
     * @param expectedStatusCode
     * @throws Exception
     */
    public static void makeCall(String URL, String user, String pass, int expectedStatusCode) throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()){
            HttpGet httpget = new HttpGet(URL);

            HttpResponse response = httpClient.execute(httpget);

            HttpEntity entity = response.getEntity();
            if (entity != null) { EntityUtils.consume(entity); }

            // We should get the Login Page
            StatusLine statusLine = response.getStatusLine();

            assertEquals(200, statusLine.getStatusCode());

            // We should now login with the user name and password
            HttpPost httpost = new HttpPost(URL + "/j_security_check");

            List<NameValuePair> nvps = new ArrayList<>();
            nvps.add(new BasicNameValuePair("j_username", user));
            nvps.add(new BasicNameValuePair("j_password", pass));

            httpost.setEntity(new UrlEncodedFormEntity(nvps, StandardCharsets.UTF_8));

            response = httpClient.execute(httpost);
            entity = response.getEntity();
            if (entity != null) { EntityUtils.consume(entity); }

            statusLine = response.getStatusLine();

            // Post authentication - we have a 302
            assertEquals(302, statusLine.getStatusCode());
            Header locationHeader = response.getFirstHeader("Location");
            String location = locationHeader.getValue();

            HttpGet httpGet = new HttpGet(location);
            response = httpClient.execute(httpGet);

            entity = response.getEntity();
            if (entity != null) { EntityUtils.consume(entity); }

            // Either the authentication passed or failed based on the expected status code
            statusLine = response.getStatusLine();
            assertEquals(expectedStatusCode, statusLine.getStatusCode());
        }
    }

    /**
     * Returns "secondary.test.address" system property if such exists. If not found, then there is a fallback to
     * {@link ManagementClient#getMgmtAddress()} or {@link #getDefaultHost(boolean)} (when mgmtClient is <code>null</code>).
     * Returned value can be converted to canonical hostname if useCanonicalHost==true. Returned value is not formatted for URLs
     * (i.e. square brackets are not placed around IPv6 addr - for instance "::1")
     *
     * @param mgmtClient management client instance (may be <code>null</code>)
     * @param useCanonicalHost
     * @return
     */
    public static String getSecondaryTestAddress(final ManagementClient mgmtClient, final boolean useCanonicalHost) {
        String address = System.getProperty("secondary.test.address");
        if (StringUtils.isBlank(address)) {
            address = mgmtClient != null ? mgmtClient.getMgmtAddress() : getDefaultHost(false);
        }
        if (useCanonicalHost) {
            address = getCannonicalHost(address);
        }
        return stripSquareBrackets(address);
    }

    /**
     * Returns "secondary.test.address" system property if such exists. If not found, then there is a fallback to
     * {@link ManagementClient#getMgmtAddress()}. Returned value is formatted to use in URLs (i.e. if it's IPv6 address, then
     * square brackets are placed around - e.g. "[::1]")
     *
     * @param mgmtClient management client instance (may be <code>null</code>)
     * @return
     */
    public static String getSecondaryTestAddress(final ManagementClient mgmtClient) {
        return NetworkUtils.formatPossibleIpv6Address(getSecondaryTestAddress(mgmtClient, false));
    }

    /**
     * Requests given URL and checks if the returned HTTP status code is the expected one. Returns HTTP response body
     *
     * @param url URL to which the request should be made
     * @param httpClient DefaultHttpClient to test multiple access
     * @param expectedStatusCode expected status code returned from the requested server
     * @return HTTP response body
     * @throws IOException
     * @throws URISyntaxException
     */
    public static String makeCallWithHttpClient(URL url, HttpClient httpClient, int expectedStatusCode) throws IOException,
            URISyntaxException {

        String httpResponseBody = null;
        HttpGet httpGet = new HttpGet(url.toURI());
        HttpResponse response = httpClient.execute(httpGet);
        int statusCode = response.getStatusLine().getStatusCode();
        LOGGER.trace("Request to: " + url + " responds: " + statusCode);

        assertEquals("Unexpected status code", expectedStatusCode, statusCode);

        HttpEntity entity = response.getEntity();
        if (entity != null) {
            httpResponseBody = EntityUtils.toString(response.getEntity());
            EntityUtils.consume(entity);
        }
        return httpResponseBody;
    }

    /**
     * Returns response body for the given URL request as a String. It also checks if the returned HTTP status code is the
     * expected one. If the server returns {@link HttpServletResponse#SC_UNAUTHORIZED} and username is provided, then a new
     * request is created with the provided credentials (basic authentication).
     *
     * @param url URL to which the request should be made
     * @param user Username (may be null)
     * @param pass Password (may be null)
     * @param expectedStatusCode expected status code returned from the requested server
     * @return HTTP response body
     * @throws IOException
     * @throws URISyntaxException
     */
    public static String makeCallWithBasicAuthn(URL url, String user, String pass, int expectedStatusCode) throws IOException,
            URISyntaxException {
        return makeCallWithBasicAuthn(url, user, pass, expectedStatusCode, false);
    }

    /**
     * Returns response body for the given URL request as a String. It also checks if the returned HTTP status code is the
     * expected one. If the server returns {@link HttpServletResponse#SC_UNAUTHORIZED} and username is provided, then a new
     * request is created with the provided credentials (basic authentication).
     *
     * @param url URL to which the request should be made
     * @param user Username (may be null)
     * @param pass Password (may be null)
     * @param expectedStatusCode expected status code returned from the requested server
     * @param checkFollowupAuthState whether to check auth state for followup request - if set to true, followup
     *                               request is sent to server and 200 OK is expected directly (no re-authentication
     *                               challenge - 401 Unauthorized - is expected)
     * @return HTTP response body
     * @throws IOException
     * @throws URISyntaxException
     */
    public static String makeCallWithBasicAuthn(URL url, String user, String pass, int expectedStatusCode, boolean
            checkFollowupAuthState) throws IOException, URISyntaxException {
        LOGGER.trace("Requesting URL " + url);

        // use UTF-8 charset for credentials
        Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
                .register(AuthSchemes.BASIC, new BasicSchemeFactory(StandardCharsets.UTF_8))
                .register(AuthSchemes.DIGEST, new DigestSchemeFactory(StandardCharsets.UTF_8))
                .build();
        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setDefaultAuthSchemeRegistry(authSchemeRegistry)
                .build()){
            final HttpGet httpGet = new HttpGet(url.toURI());
            HttpResponse response = httpClient.execute(httpGet);
            int statusCode = response.getStatusLine().getStatusCode();
            if (HttpServletResponse.SC_UNAUTHORIZED != statusCode || StringUtils.isEmpty(user)) {
                assertEquals("Unexpected HTTP response status code.", expectedStatusCode, statusCode);
                return EntityUtils.toString(response.getEntity());
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("HTTP response was SC_UNAUTHORIZED, let's authenticate the user " + user);
            }
            HttpEntity entity = response.getEntity();
            if (entity != null)
                EntityUtils.consume(entity);

            final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(user, pass);

            HttpClientContext hc = new HttpClientContext();
            hc.setCredentialsProvider(new BasicCredentialsProvider());
            hc.getCredentialsProvider().setCredentials(new AuthScope(url.getHost(), url.getPort()), credentials);
            //enable auth
            response = httpClient.execute(httpGet, hc);
            statusCode = response.getStatusLine().getStatusCode();
            assertEquals("Unexpected status code returned after the authentication.", expectedStatusCode, statusCode);

            if (checkFollowupAuthState) {
                // Let's disable authentication for this client as we already have all the context necessary to be
                // authorized (we expect that gained 'nonce' value can be re-used in our case here).
                // By disabling authentication we simply get first server response and thus we can check whether we've
                // got 200 OK or different response code.
                RequestConfig reqConf = RequestConfig.custom().setAuthenticationEnabled(false).build();
                httpGet.setConfig(reqConf);
                response = httpClient.execute(httpGet, hc);
                statusCode = response.getStatusLine().getStatusCode();
                assertEquals("Unexpected status code returned after the authentication.", HttpURLConnection.HTTP_OK,
                        statusCode);
            }

            return EntityUtils.toString(response.getEntity());
        }
    }

    /**
     * Do HTTP request using given client with BEARER_TOKEN authentication.The implementation makes 2 calls - the first without
     * Authorization header provided (just to check response code and WWW-Authenticate header value), the second with
     * Authorization header.
     *
     * @param url URL to make request to
     * @param token bearer token
     * @param expectedStatusCode expected status code
     * @return response body
     * @throws URISyntaxException
     * @throws UnsupportedEncodingException
     * @throws ClientProtocolException
     */
    public static String makeCallWithTokenAuthn(final URL url, String token, int expectedStatusCode)
            throws URISyntaxException, IOException, ClientProtocolException {

        LOGGER.trace("Requesting URL: " + url);

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().setRedirectStrategy(REDIRECT_STRATEGY).build()) {
            final HttpGet httpGet = new HttpGet(url.toURI());
            HttpResponse response = httpClient.execute(httpGet);
            assertEquals("Unexpected HTTP response status code.", SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
            Header[] authenticateHeaders = response.getHeaders("WWW-Authenticate");
            assertTrue("Expected WWW-Authenticate header was not present in the HTTP response",
                    authenticateHeaders != null && authenticateHeaders.length > 0);
            boolean bearerAuthnHeaderFound = false;
            for (Header header : authenticateHeaders) {
                final String headerVal = header.getValue();
                if (headerVal != null && headerVal.startsWith("Bearer")) {
                    bearerAuthnHeaderFound = true;
                    break;
                }
            }
            assertTrue("WWW-Authenticate response header didn't request expected Bearer token authentication",
                    bearerAuthnHeaderFound);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                EntityUtils.consume(entity);
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(String.format("HTTP response was SC_UNAUTHORIZED, let's authenticate using the token '%s'", token));
            }

            httpGet.addHeader("Authorization", "Bearer " + token);
            response = httpClient.execute(httpGet);
            assertEquals("Unexpected status code returned after the authentication.", expectedStatusCode,
                    response.getStatusLine().getStatusCode());
            return EntityUtils.toString(response.getEntity());
        }
    }

    /**
     * Generates content of jboss-ejb3.xml file as a ShrinkWrap asset with the given security domain name.
     *
     * @param securityDomain security domain name
     * @return Asset instance
     */
    public static Asset getJBossEjb3XmlAsset(final String securityDomain) {
        String sb = "<jboss:ejb-jar xmlns:jboss='http://www.jboss.com/xml/ns/javaee'" +
                "\n\txmlns='http://java.sun.com/xml/ns/javaee'" +
                "\n\txmlns:s='urn:security'" +
                "\n\tversion='3.1'" +
                "\n\timpl-version='2.0'>" +
                "\n\t<assembly-descriptor><s:security>" +
                "\n\t\t<ejb-name>*</ejb-name>" +
                "\n\t\t<s:security-domain>" + securityDomain + "</s:security-domain>" +
                "\n\t</s:security></assembly-descriptor>" +
                "\n</jboss:ejb-jar>";
        return new StringAsset(sb);
    }

    /**
     * Generates content of jboss-web.xml file as a ShrinkWrap asset with the given security domain name and given valve class.
     *
     * @param securityDomain security domain name (not-<code>null</code>)
     * @param valveClassNames valve class (e.g. an Authenticator) which should be added to jboss-web file (may be
     *        <code>null</code>)
     * @return Asset instance
     */
    public static Asset getJBossWebXmlAsset(final String securityDomain, final String... valveClassNames) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<jboss-web>");
        sb.append("\n\t<security-domain>").append(securityDomain).append("</security-domain>");
        if (valveClassNames != null) {
            for (String valveClassName : valveClassNames) {
                if (StringUtils.isNotEmpty(valveClassName)) {
                    sb.append("\n\t<valve><class-name>").append(valveClassName).append("</class-name></valve>");
                }
            }
        }
        sb.append("\n</jboss-web>");
        return new StringAsset(sb.toString());
    }

    /**
     * Generates content of the jboss-deployment-structure.xml deployment descriptor as a ShrinkWrap asset. It fills the given
     * dependencies (module names) into it.
     *
     * @param dependencies AS module names
     * @return
     */
    public static Asset getJBossDeploymentStructure(String... dependencies) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<jboss-deployment-structure><deployment><dependencies>");
        if (dependencies != null) {
            for (String moduleName : dependencies) {
                sb.append("\n\t<module name='").append(moduleName).append("'/>");
            }
        }
        sb.append("\n</dependencies></deployment></jboss-deployment-structure>");
        return new StringAsset(sb.toString());
    }

    /**
     * Creates content of users.properties and/or roles.properties files for given array of role names.
     * <p>
     * For instance if you provide 2 roles - "role1", "role2" then the result will be:
     *
     * <pre>
     * role1=role1
     * role2=role2
     * </pre>
     *
     * If you use it as users.properties and roles.properties, then <code>roleName == userName == password</code>
     *
     * @param roles role names (used also as user names and passwords)
     * @return not-<code>null</code> content of users.properties and/or roles.properties
     */
    public static String createUsersFromRoles(String... roles) {
        final StringBuilder sb = new StringBuilder();
        if (roles != null) {
            for (String role : roles) {
                sb.append(role).append("=").append(role).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Strips square brackets - '[' and ']' from the given string. It can be used for instance to remove the square brackets
     * around IPv6 address in a URL.
     *
     * @param str string to strip
     * @return str without square brackets in it
     */
    public static String stripSquareBrackets(final String str) {
        return StringUtils.strip(str, "[]");
    }

    /**
     * Fixes/replaces LDAP bind address in the CreateTransport annotation of ApacheDS.
     *
     * @param createLdapServer
     * @param address
     */
    public static void fixApacheDSTransportAddress(ManagedCreateLdapServer createLdapServer, String address) {
        final CreateTransport[] createTransports = createLdapServer.transports();
        for (int i = 0; i < createTransports.length; i++) {
            final ManagedCreateTransport mgCreateTransport = new ManagedCreateTransport(createTransports[i]);
            // localhost is a default used in original CreateTransport annotation. We use it as a fallback.
            mgCreateTransport.setAddress(address != null ? address : "localhost");
            createTransports[i] = mgCreateTransport;
        }
    }

    /**
     * Copies server and clients keystores and truststores from this package to the given folder. Server truststore has accepted
     * certificate from client keystore and vice-versa
     *
     * @param workingFolder folder to which key material should be copied
     * @throws IOException copying of keystores fails
     * @throws IllegalArgumentException workingFolder is null or it's not a directory
     */
    public static void createKeyMaterial(final File workingFolder) throws IOException, IllegalArgumentException {
        if (workingFolder == null || !workingFolder.isDirectory()) {
            throw new IllegalArgumentException("Provide an existing folder as the method parameter.");
        }
        try {
            generateKeyMaterial(workingFolder);
        } catch (IOException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Unable to generate key material");
        }
        LOGGER.trace("Key material created in " + workingFolder.getAbsolutePath());
    }

    /**
     * Makes HTTP call without authentication. Returns response body as a String.
     *
     * @param uri requested URL
     * @param expectedStatusCode expected status code - it's checked after the request is executed
     * @throws Exception
     */
    public static String makeCall(URI uri, int expectedStatusCode) throws Exception {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()){
            final HttpGet httpget = new HttpGet(uri);
            final HttpResponse response = httpClient.execute(httpget);
            int statusCode = response.getStatusLine().getStatusCode();
            assertEquals("Unexpected status code in HTTP response.", expectedStatusCode, statusCode);
            return EntityUtils.toString(response.getEntity());
        }
    }

    /**
     * Returns param/value pair in form "urlEncodedName=urlEncodedValue". It can be used for instance in HTTP get queries.
     *
     * @param paramName parameter name
     * @param paramValue parameter value
     * @return "[urlEncodedName]=[urlEncodedValue]" string
     */
    public static String encodeQueryParam(final String paramName, final String paramValue) {
        return StringUtils.isEmpty(paramValue) ? null : (URLEncoder.encode(paramName, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(
                StringUtils.defaultString(paramValue, StringUtils.EMPTY), StandardCharsets.UTF_8));
    }

    /**
     * Returns management address (host) from the givem {@link ManagementClient}. If the
     * returned value is IPv6 address then square brackets around are stripped.
     *
     * @param managementClient
     * @return
     */
    public static String getHost(final ManagementClient managementClient) {
        return CoreUtils.stripSquareBrackets(managementClient.getMgmtAddress());
    }

    /**
     * Returns hostname - either read from the "node0" system property or the loopback address "127.0.0.1".
     *
     * @param canonical return hostname in canonical form
     *
     * @return
     */
    public static String getDefaultHost(boolean canonical) {
        final String hostname = TestSuiteEnvironment.getHttpAddress();
        return canonical ? getCannonicalHost(hostname) : hostname;
    }

    /**
     * Returns installed login configuration.
     *
     * @return Configuration
     */
    public static Configuration getLoginConfiguration() {
        Configuration configuration = null;
        try {
            configuration = Configuration.getConfiguration();
        } catch (SecurityException e) {
            LOGGER.debug("Unable to load default login configuration", e);
        }
        return configuration;
    }

    /**
     * Creates login context for given {@link Krb5LoginConfiguration} and credentials and calls the {@link LoginContext#login()}
     * method on it. This method contains workaround for IBM JDK issue described in bugzilla <a
     * href="https://bugzilla.redhat.com/show_bug.cgi?id=1206177">https://bugzilla.redhat.com/show_bug.cgi?id=1206177</a>.
     *
     * @param krb5Configuration
     * @param user
     * @param pass
     * @return
     * @throws LoginException
     */
    public static LoginContext loginWithKerberos(final Krb5LoginConfiguration krb5Configuration, final String user,
            final String pass) throws LoginException {
        LoginContext lc = new LoginContext(krb5Configuration.getName(), new UsernamePasswordCBH(user, pass.toCharArray()));
        lc.login();
        return lc;
    }


    /**
     * Creates a temporary folder name with given name prefix.
     *
     * @param prefix folder name prefix
     * @return created folder
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static File createTemporaryFolder(String prefix) throws IOException {
        File file = File.createTempFile(prefix, "", null);
        LOGGER.debugv("Creating temporary folder {0}", file);
        file.delete();
        file.mkdir();
        return file;
    }

    private static class UsernamePasswordCBH implements CallbackHandler {

        /*
         * Note: We use CallbackHandler implementations like this in test cases as test cases need to run unattended, a true
         * CallbackHandler implementation should interact directly with the current user to prompt for the username and
         * password.
         *
         * i.e. In a client app NEVER prompt for these values in advance and provide them to a CallbackHandler like this.
         */

        private final String username;
        private final char[] password;

        private UsernamePasswordCBH(final String username, final char[] password) {
            this.username = username;
            this.password = password;
        }

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback current : callbacks) {
                if (current instanceof NameCallback) {
                    NameCallback ncb = (NameCallback) current;
                    ncb.setName(username);
                } else if (current instanceof PasswordCallback) {
                    PasswordCallback pcb = (PasswordCallback) current;
                    pcb.setPassword(password);
                } else {
                    throw new UnsupportedCallbackException(current);
                }
            }

        }

    }
}
