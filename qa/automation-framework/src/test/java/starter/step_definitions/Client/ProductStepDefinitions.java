package starter.step_definitions.Client;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import starter.utils.TestDataLoader;
import starter.utils.model.requestModel.client.ProductCreate;

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

public class ProductStepDefinitions {

    private static final String PRODUCT_ID = "PRODUCT_ID";
    private static final String PRODUCT_NAME = "PRODUCT_NAME";
    private static final String SUBMITTED_PRODUCT_NAME = "SUBMITTED_PRODUCT_NAME";

    // ---------- Create ----------

    @When("the user sends {string} request with {string} to create a new product")
    public void theUserSendsRequestWithToCreateANewProduct(String endpoint, String product) {
        send("POST", endpoint, null, convertObjectToJson(productBody(product)));
        Response response = getContext(HTTP_RESPONSE.name());
        setContext(PRODUCT_ID, extractJsonValue(response, "data.id"));
        setContext(PRODUCT_NAME, extractJsonValue(response, "data.name"));
    }

    @When("the user sends {string} request with {string} to create another product")
    public void theUserSendsRequestWithToCreateAnotherProduct(String endpoint, String product) {
        // Does not replace the saved product that later scenarios read and update
        send("POST", endpoint, null, convertObjectToJson(productBody(product)));
    }

    @When("the user sends {string} request to create a product with the existing product's name")
    public void theUserSendsRequestToCreateAProductWithTheExistingProductsName(String endpoint) {
        String existingName = getContext(PRODUCT_NAME);
        if (existingName == null) {
            throw new IllegalStateException("No product name saved. Run the 'create a new product' scenario first.");
        }
        ProductCreate body = new ProductCreate();
        body.setName(existingName);
        body.setPrice(1.0);
        body.setQuantity(1);
        send("POST", endpoint, null, convertObjectToJson(body));
    }

    // ---------- Read ----------

    @When("the user sends {string} request to get the created product by id")
    public void theUserSendsRequestToGetTheCreatedProductById(String endpoint) {
        send("GET", endpoint, createdProductId(), null);
    }

    @When("the user sends {string} request to get the product with id {string}")
    public void theUserSendsRequestToGetTheProductWithId(String endpoint, String productId) {
        send("GET", endpoint, productId, null);
    }

    @When("the user sends {string} request to get all existing products")
    public void theUserSendsRequestToGetAllExistingProducts(String endpoint) {
        send("GET", endpoint, null, null);
    }

    @Then("the response should return the created product")
    public void theResponseShouldReturnTheCreatedProduct() {
        assertEquals(createdProductId(), extractJsonValue(getContext(HTTP_RESPONSE.name()), "data.id"), "data.id");
    }

    @Then("the product list should contain the created product")
    public void theProductListShouldContainTheCreatedProduct() {
        Response response = getContext(HTTP_RESPONSE.name());
        String ids = String.valueOf(response.jsonPath().getList("data.id"));
        assertTrue(response.jsonPath().getList("data.id", String.class).contains(createdProductId()),
                "Product " + createdProductId() + " not in list " + ids);
    }

    // ---------- Update ----------

    @When("the user sends {string} request with {string} to update the created product")
    public void theUserSendsRequestWithToUpdateTheCreatedProduct(String endpoint, String product) {
        ProductCreate body = productBody(product);
        setContext(SUBMITTED_PRODUCT_NAME, body.getName());
        send("PUT", endpoint, createdProductId(), convertObjectToJson(body));
        // The duplicate-name check later must use the new name
        setContext(PRODUCT_NAME, extractJsonValue(getContext(HTTP_RESPONSE.name()), "data.name"));
    }

    @When("the user sends {string} request with {string} to update the product with id {string}")
    public void theUserSendsRequestWithToUpdateTheProductWithId(String endpoint, String product, String productId) {
        send("PUT", endpoint, productId, convertObjectToJson(productBody(product)));
    }

    @Then("the response should contain the submitted product name")
    public void theResponseShouldContainTheSubmittedProductName() {
        assertEquals(getContext(SUBMITTED_PRODUCT_NAME),
                extractJsonValue(getContext(HTTP_RESPONSE.name()), "data.name"), "data.name");
    }

    @Then("the validation message should mention {string}")
    public void theValidationMessageShouldMention(String expected) {
        // NestJS sends "message" as a string or a list of strings; getString covers both
        String message = extractJsonValue(getContext(HTTP_RESPONSE.name()), "message");
        assertTrue(message != null && message.contains(expected),
                "Expected message to mention '" + expected + "' but was: " + message);
    }

    // ---------- Helpers ----------

    /** Loads a product from product_create.json and fills in {{uid}} so every run is unique. */
    private ProductCreate productBody(String product) {
        ProductCreate body = TestDataLoader.getProductCreateData(product);
        body.setName(body.getName().replace("{{uid}}", String.valueOf(System.currentTimeMillis())));
        return body;
    }

    private String createdProductId() {
        String productId = getContext(PRODUCT_ID);
        if (productId == null) {
            throw new IllegalStateException("No product id saved. Run the 'create a new product' scenario first.");
        }
        return productId;
    }

    private void send(String method, String endpoint, String productId, String body) {
        Map<String, String> pathParams = productId == null ? null : Map.of("productid", productId);
        Response response = requestWithPathParams(method, null, getParameterProperties(endpoint),
                pathParams, null, body);
        setContext(HTTP_RESPONSE.name(), response);
    }
}
