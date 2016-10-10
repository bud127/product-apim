/*
 *   Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.am.integration.tests.websocket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.json.JSONObject;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.am.integration.test.utils.APIManagerIntegrationTestException;
import org.wso2.am.integration.test.utils.base.APIMIntegrationBaseTest;
import org.wso2.am.integration.test.utils.base.APIMIntegrationConstants;
import org.wso2.am.integration.test.utils.bean.APILifeCycleState;
import org.wso2.am.integration.test.utils.bean.APILifeCycleStateRequest;
import org.wso2.am.integration.test.utils.bean.APIRequest;
import org.wso2.am.integration.test.utils.bean.APPKeyRequestGenerator;
import org.wso2.am.integration.test.utils.bean.SubscriptionRequest;
import org.wso2.am.integration.test.utils.clients.APIPublisherRestClient;
import org.wso2.am.integration.test.utils.clients.APIStoreRestClient;
import org.wso2.am.integration.test.utils.generic.APIMTestCaseUtils;
import org.wso2.am.integration.tests.websocket.client.ToUpperClientSocket;
import org.wso2.am.integration.tests.websocket.server.ToUpperWebSocket;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2.carbon.utils.xml.StringUtils;

import javax.ws.rs.core.Response;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class WebSocketAPIPublishTestCase extends APIMIntegrationBaseTest {
    private static final long WAIT_TIME = 30 * 1000;
    private String testMessage = "WebSocketMessage1";
    private final Log log = LogFactory.getLog(WebSocketAPIPublishTestCase.class);
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    private APIIdentifier apiIdentifierWebSocketTest;
    private APIPublisherRestClient apiPublisher;
    private String provider;
    private static final String API_NAME = "WebSocketAPI";
    private static final String API_VERSION = "1.0.0";
    private static final int BACKEND_SERVER_PORT = 8580;
    private String applicationNameTest1 = "WebSocketApplication";

    @Factory(dataProvider = "userModeDataProvider")
    public WebSocketAPIPublishTestCase(TestUserMode userMode) {
        this.userMode = userMode;
    }

    @DataProvider
    public static Object[][] userModeDataProvider() {
        return new Object[][]{
                new Object[]{TestUserMode.SUPER_TENANT_ADMIN},
        };
    }

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws APIManagerIntegrationTestException {
        super.init(userMode);
        startWebSocketServer(BACKEND_SERVER_PORT);
    }

    @Test(description = "Publish WebSocket API")
    public void publishWebSocketAPI() throws APIManagerIntegrationTestException, URISyntaxException, XPathExpressionException {
        apiPublisher = new APIPublisherRestClient(getPublisherURLHttp());
        apiStore = new APIStoreRestClient(getStoreURLHttp());
        provider = user.getUserName();
        String apiContext = "echo";
        String endpointUri = "ws://localhost:" + BACKEND_SERVER_PORT;

        //Create the api creation request object
        APIRequest apiRequest = new APIRequest(API_NAME, apiContext, new URI(endpointUri));
        apiRequest.setVersion(API_VERSION);
        apiRequest.setTiersCollection("Unlimited");
        apiRequest.setTier("Unlimited");
        apiRequest.setProvider(provider);
        apiRequest.setWs("true");
        apiPublisher.login(user.getUserName(),
                user.getPassword());
        apiPublisher.addAPI(apiRequest);

        //publishing API
        APILifeCycleStateRequest updateRequest =
                new APILifeCycleStateRequest(API_NAME, user.getUserName(),
                        APILifeCycleState.PUBLISHED);
        apiPublisher.changeAPILifeCycleStatus(updateRequest);
        apiIdentifierWebSocketTest = new APIIdentifier(provider, API_NAME, API_VERSION);

        apiPublisher.login(publisherContext.getContextTenant().getContextUser().getUserName(),
                publisherContext.getContextTenant().getContextUser().getPassword());
        apiStore.login(storeContext.getContextTenant().getContextUser().getUserName(),
                storeContext.getContextTenant().getContextUser().getPassword());

        List<APIIdentifier> publisherAPIList = APIMTestCaseUtils.
                getAPIIdentifierListFromHttpResponse(apiPublisher.getAllAPIs());
        assertTrue(APIMTestCaseUtils.isAPIAvailable(apiIdentifierWebSocketTest, publisherAPIList),
                "Published Api is visible in API Publisher.");

        List<APIIdentifier> storeAPIList = APIMTestCaseUtils.
                getAPIIdentifierListFromHttpResponse(apiStore.getAllPublishedAPIs());
        assertTrue(APIMTestCaseUtils.isAPIAvailable(apiIdentifierWebSocketTest, storeAPIList),
                "Published Api is visible in API Store.");
    }

    @Test(description = "Create Application and subscribe", dependsOnMethods = "publishWebSocketAPI")
    public void testWebSocketAPIApplicationSubscription() throws Exception {
        waitForAPIDeployment();
        apiStore.addApplication(applicationNameTest1, APIMIntegrationConstants.API_TIER.UNLIMITED, "", "");
        SubscriptionRequest subscriptionRequest =
                new SubscriptionRequest(API_NAME, provider);
        subscriptionRequest.setApplicationName(applicationNameTest1);
        subscriptionRequest.setTier(APIMIntegrationConstants.API_TIER.UNLIMITED);
        //Validate Subscription of the API
        HttpResponse subscribeApiResponse = apiStore.subscribe(subscriptionRequest);
        assertEquals(subscribeApiResponse.getResponseCode(), Response.Status.OK.getStatusCode(),
                API_NAME + "is not Subscribed");
        assertTrue(subscribeApiResponse.getData().contains("\"error\" : false"),
                API_NAME + "is not Subscribed");
    }

    @Test(description = "Invoke API using token", dependsOnMethods = "testWebSocketAPIApplicationSubscription")
    public void testWebsocketAPIInvocation() throws Exception {
        APPKeyRequestGenerator generateAppKeyRequestSandBox =
                new APPKeyRequestGenerator(applicationNameTest1);
        generateAppKeyRequestSandBox.setKeyType("SANDBOX");
        String responseSandBox = apiStore.generateApplicationKey
                (generateAppKeyRequestSandBox).getData();
        JSONObject jsonObject = new JSONObject(responseSandBox);
        String sandboxAccessToken =
                jsonObject.getJSONObject("data").getJSONObject("key").get("accessToken").toString();
        log.info("Sandbox token " + sandboxAccessToken);
        String dest = "ws://127.0.0.1:9099/echo/1.0.0";
        WebSocketClient client = new WebSocketClient();
        try {
            ToUpperClientSocket socket = new ToUpperClientSocket();
            client.start();
            URI echoUri = new URI(dest);
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            request.setHeader("Authorization", "Bearer " + sandboxAccessToken);
            client.connect(socket, echoUri, request);
            socket.getLatch().await(3, TimeUnit.SECONDS);
            socket.sendMessage(testMessage);
            waitForReply(socket, testMessage);
            assertEquals(StringUtils.isEmpty(socket.getResponseMessage()), false,
                    "Client did not receive response from server");
            socket.setResponseMessage(null);
        } catch (InterruptedException e) {
            log.error("Exception in connecting to server", e);
            assertTrue(false, "Client cannot connect to server");
        } finally {
            try {
                client.stop();
            } catch (Exception e) {
                log.error("Exception in disconnecting from server", e);
            }
        }
    }

    @Test(description = "Invoke API using invalid token", dependsOnMethods = "testWebsocketAPIInvocation")
    public void testWebsocketAPIInvalidTokenInvocation() {
        String dest = "ws://127.0.0.1:9099/echo/1.0.0";
        WebSocketClient client = new WebSocketClient();
        try {
            ToUpperClientSocket socket = new ToUpperClientSocket();
            client.start();
            URI echoUri = new URI(dest);
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            request.setHeader("Authorization", "Bearer " + "00000000-0000-0000-0000-000000000000");
            client.connect(socket, echoUri, request);
            socket.getLatch().await(3, TimeUnit.SECONDS);
            socket.sendMessage(testMessage);
            waitForReply(socket, testMessage);
            assertEquals(StringUtils.isEmpty(socket.getResponseMessage()), true,
                    "Client did not receive response from server");
            socket.setResponseMessage(null);
        } catch (Exception e) {
            log.error("Exception in connecting to server", e);
            assertTrue(true, "Client cannot connect to server");
        } finally {
            try {
                client.stop();
            } catch (Exception e) {
                log.error("Exception in disconnecting from server", e);
            }
        }
    }

    /**
     * Wait for client to receive reply from the server
     *
     * @param clientSocket
     * @param expectedMessage
     */
    private void waitForReply(ToUpperClientSocket clientSocket, String expectedMessage) {
        long currentTime = System.currentTimeMillis();
        long waitTime = currentTime + WAIT_TIME;
        while (StringUtils.isEmpty(clientSocket.getResponseMessage()) && waitTime > System.currentTimeMillis()) {
            try {
                log.info("Waiting for reply from server:");
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }
        log.info("Client received :" + clientSocket.getResponseMessage());
    }

    /**
     * Starts backend web socket server in given port
     *
     * @param serverPort
     */
    private void startWebSocketServer(final int serverPort) {
        executorService.execute(new Runnable() {
            public void run() {
                WebSocketHandler wsHandler = new WebSocketHandler() {
                    @Override
                    public void configure(WebSocketServletFactory factory) {
                        factory.register(ToUpperWebSocket.class);
                    }
                };
                Server server = new Server(serverPort);
                server.setHandler(wsHandler);
                try {
                    server.start();
                } catch (InterruptedException ignore) {
                } catch (Exception e) {
                    log.error("Error while starting server ", e);
                }
            }

        });
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {
        super.cleanUp();
        executorService.shutdownNow();
    }
}