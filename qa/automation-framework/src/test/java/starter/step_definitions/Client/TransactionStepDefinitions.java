package starter.step_definitions.Client;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import starter.utils.TestDataLoader;
import starter.utils.model.requestModel.client.ProductCreate;
import starter.utils.model.requestModel.client.TransactionCreate;
import starter.utils.model.requestModel.client.UserCreate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static starter.utils.HelperUtils.convertObjectToJson;
import static starter.utils.HelperUtils.extractJsonValue;
import static starter.utils.HttpApiUtils.requestWithPathParams;
import static starter.utils.PropertiesReader.getParameterProperties;
import static starter.utils.TestGlobalVariables.ContextEnum.HTTP_RESPONSE;
import static starter.utils.TestGlobalVariables.getContext;
import static starter.utils.TestGlobalVariables.setContext;

public class TransactionStepDefinitions {

    // Own keys, so these scenarios never overwrite the ids the Users/Products features keep
    private static final String TX_USER_ID = "TX_USER_ID";
    private static final String TX_PRODUCT_ID = "TX_PRODUCT_ID";
    private static final String TX_ID = "TX_ID";

    // ---------- Setup ----------

    @Given("a registered user and a product with {int} units in stock")
    public void aRegisteredUserAndAProductWithUnitsInStock(int stock) {
        String uid = uid();

        UserCreate user = new UserCreate();
        user.setName("QA Buyer " + uid);
        user.setEmail("qa.buyer." + uid + "@example.test");
        Response userRes = send("POST", "USER_CREATE_URL", null, convertObjectToJson(user));
        assertEquals(201, userRes.statusCode(), "Setup: create user failed: " + userRes.asString());
        setContext(TX_USER_ID, extractJsonValue(userRes, "data.id"));

        ProductCreate product = new ProductCreate();
        product.setName("QA Tx Product " + uid);
        product.setPrice(20.0);
        product.setQuantity(stock);
        Response productRes = send("POST", "PRODUCT_CREATE_URL", null, convertObjectToJson(product));
        assertEquals(201, productRes.statusCode(), "Setup: create product failed: " + productRes.asString());
        setContext(TX_PRODUCT_ID, extractJsonValue(productRes, "data.id"));

        // setContext ignores null, so clear the previous scenario's transaction with ""
        setContext(TX_ID, "");
    }

    @Given("the user has bought the product with {string}")
    public void theUserHasBoughtTheProductWith(String transaction) {
        Response response = buy("TRANSACTION_CREATE_URL", transaction);
        assertEquals(201, response.statusCode(), "Setup: purchase failed: " + response.asString());
    }

    // ---------- Create ----------

    @When("the user sends {string} request with {string} to buy the product")
    public void theUserSendsRequestWithToBuyTheProduct(String endpoint, String transaction) {
        buy(endpoint, transaction);
    }

    @When("the user sends {string} transaction request with the following body")
    public void theUserSendsTransactionRequestWithTheFollowingBody(String endpoint, String body) {
        String resolved = body
                .replace("{{userId}}", userId())
                .replace("{{productId}}", productId());
        send("POST", endpoint, null, resolved);
    }

    // ---------- Read ----------

    @When("the user sends {string} request to get the created transaction by id")
    public void theUserSendsRequestToGetTheCreatedTransactionById(String endpoint) {
        send("GET", endpoint, transactionId(), null);
    }

    @When("the user sends {string} request to get the transaction with id {string}")
    public void theUserSendsRequestToGetTheTransactionWithId(String endpoint, String transactionId) {
        send("GET", endpoint, transactionId, null);
    }

    @When("the user sends {string} request to get all existing transactions")
    public void theUserSendsRequestToGetAllExistingTransactions(String endpoint) {
        send("GET", endpoint, null, null);
    }

    // ---------- Assertions ----------

    @Then("the transaction should be linked to the user and product with quantity {int}")
    public void theTransactionShouldBeLinkedToTheUserAndProductWithQuantity(int quantity) {
        Response response = getContext(HTTP_RESPONSE.name());
        assertEquals(userId(), extractJsonValue(response, "data.user.id"), "data.user.id");
        assertEquals(productId(), extractJsonValue(response, "data.product.id"), "data.product.id");
        assertEquals(String.valueOf(quantity), extractJsonValue(response, "data.quantity"), "data.quantity");
    }

    @Then("the response should return the created transaction")
    public void theResponseShouldReturnTheCreatedTransaction() {
        assertEquals(transactionId(), extractJsonValue(getContext(HTTP_RESPONSE.name()), "data.id"), "data.id");
    }

    @Then("the transaction list should contain the created transaction")
    public void theTransactionListShouldContainTheCreatedTransaction() {
        Response response = getContext(HTTP_RESPONSE.name());
        assertTrue(response.jsonPath().getList("data.id", String.class).contains(transactionId()),
                "Transaction " + transactionId() + " not in the list");
    }

    @Then("the response message should contain {string}")
    public void theResponseMessageShouldContain(String expected) {
        // NestJS sends "message" as a string or a list of strings; getString covers both
        String message = extractJsonValue(getContext(HTTP_RESPONSE.name()), "message");
        assertTrue(message != null && message.contains(expected),
                "Expected message to contain '" + expected + "' but was: " + message);
    }

    @Then("the product stock should be {int}")
    public void theProductStockShouldBe(int expected) {
        assertEquals(String.valueOf(expected), extractJsonValue(getProduct(), "data.quantity"), "product quantity");
    }

    @Then("the product status should be {string}")
    public void theProductStatusShouldBe(String expected) {
        assertEquals(expected, extractJsonValue(getProduct(), "data.status"), "product status");
    }

    // ---------- Helpers ----------

    /** Loads a transaction from transaction_create.json; missing ids default to this scenario's user/product. */
    private Response buy(String endpoint, String transaction) {
        TransactionCreate data = TestDataLoader.getTransactionCreateData(transaction);
        TransactionCreate body = new TransactionCreate();
        body.setUserId(data.getUserId() != null ? data.getUserId() : Integer.valueOf(userId()));
        body.setProductId(data.getProductId() != null ? data.getProductId() : Integer.valueOf(productId()));
        body.setQuantity(data.getQuantity());

        Response response = send("POST", endpoint, null, convertObjectToJson(body));
        if (response.statusCode() == 201) {
            setContext(TX_ID, extractJsonValue(response, "data.id"));
        }
        return response;
    }

    /** Reads the scenario's product without replacing the response under test. */
    private Response getProduct() {
        return requestWithPathParams("GET", null, getParameterProperties("PRODUCT_GET_BY_ID_URL"),
                Map.of("productid", productId()), null, null);
    }

    private String userId() {
        return required(TX_USER_ID, "user");
    }

    private String productId() {
        return required(TX_PRODUCT_ID, "product");
    }

    private String transactionId() {
        return required(TX_ID, "transaction");
    }

    private String required(String key, String what) {
        String value = getContext(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("No " + what + " id saved for this scenario. Check the Background/Given steps.");
        }
        return value;
    }

    private static String uid() {
        return System.currentTimeMillis() + "" + (int) (Math.random() * 1000);
    }

    private Response send(String method, String endpoint, String transactionId, String body) {
        Map<String, String> pathParams = transactionId == null ? null : Map.of("transactionid", transactionId);
        Response response = requestWithPathParams(method, null, getParameterProperties(endpoint),
                pathParams, null, body);
        setContext(HTTP_RESPONSE.name(), response);
        return response;
    }
}
