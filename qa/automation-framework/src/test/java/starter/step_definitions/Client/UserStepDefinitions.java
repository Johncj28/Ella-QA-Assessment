package starter.step_definitions.Client;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import starter.utils.TestDataLoader;
import starter.utils.model.requestModel.client.UserCreate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static starter.utils.HelperUtils.convertObjectToJson;
import static starter.utils.HelperUtils.extractJsonValue;
import static starter.utils.HttpApiUtils.requestWithPathParams;
import static starter.utils.PropertiesReader.getParameterProperties;
import static starter.utils.TestGlobalVariables.ContextEnum.*;
import static starter.utils.TestGlobalVariables.getContext;
import static starter.utils.TestGlobalVariables.setContext;

public class UserStepDefinitions {

    private static final String USER2_EMAIL = "USER2_EMAIL";
    private static final String SUBMITTED_NAME = "SUBMITTED_NAME";
    private static final String SUBMITTED_EMAIL = "SUBMITTED_EMAIL";

    // ---------- Create ----------

    @When("the user sends {string} request with {string} to create a new user")
    public void theUserSendsRequestWithToCreateAnUser(String endpoint, String user) {
        createUser(endpoint, user);
        setContext(USER_ID.name(), extractJsonValue(getContext(HTTP_RESPONSE.name()), "data.id"));
    }

    @When("the user sends {string} request with {string} to create a second user")
    public void theUserSendsRequestWithToCreateASecondUser(String endpoint, String user) {
        createUser(endpoint, user);
        setContext(USER2_EMAIL, extractJsonValue(getContext(HTTP_RESPONSE.name()), "data.email"));
    }

    @When("the user sends {string} request with the following body")
    public void theUserSendsRequestWithTheFollowingBody(String endpoint, String body) {
        String uid = String.valueOf(System.currentTimeMillis());
        send("POST", endpoint, null, body.replace("{{uid}}", uid));
    }

    // ---------- Read ----------

    @When("the user sends {string} request to get existing user by id")
    public void theUserSendsRequestToGetExistingUserById(String endpoint) {
        send("GET", endpoint, createdUserId(), null);
    }

    @When("the user sends {string} request to get the user with id {string}")
    public void theUserSendsRequestToGetTheUserWithId(String endpoint, String userId) {
        send("GET", endpoint, userId, null);
    }

    @When("the user sends {string} request to get all existing users")
    public void theUserSendsRequestToGetAllExistingUsers(String endpoint) {
        send("GET", endpoint, null, null);
    }

    // ---------- Update ----------

    @When("the user sends {string} request with {string} to update the created user")
    public void theUserSendsRequestWithToUpdateTheCreatedUser(String endpoint, String user) {
        UserCreate body = userBody(user);
        setContext(SUBMITTED_NAME, body.getName());
        setContext(SUBMITTED_EMAIL, body.getEmail());
        send("PUT", endpoint, createdUserId(), convertObjectToJson(body));
    }

    @When("the user sends {string} request to update the created user with the second user's email")
    public void theUserSendsRequestToUpdateTheCreatedUserWithTheSecondUsersEmail(String endpoint) {
        String takenEmail = getContext(USER2_EMAIL);
        if (takenEmail == null) {
            throw new IllegalStateException("No second user email saved. Run the 'create a second user' scenario first.");
        }
        UserCreate body = new UserCreate();
        body.setName("Clash");
        body.setEmail(takenEmail);
        send("PUT", endpoint, createdUserId(), convertObjectToJson(body));
    }

    @When("the user sends {string} request with {string} to update the user with id {string}")
    public void theUserSendsRequestWithToUpdateTheUserWithId(String endpoint, String user, String userId) {
        send("PUT", endpoint, userId, convertObjectToJson(userBody(user)));
    }

    @Then("the response should contain the submitted name and email")
    public void theResponseShouldContainTheSubmittedNameAndEmail() {
        Response response = getContext(HTTP_RESPONSE.name());
        assertEquals(getContext(SUBMITTED_NAME), extractJsonValue(response, "data.name"), "data.name");
        assertEquals(getContext(SUBMITTED_EMAIL), extractJsonValue(response, "data.email"), "data.email");
    }

    // ---------- Helpers ----------

    private void createUser(String endpoint, String user) {
        send("POST", endpoint, null, convertObjectToJson(userBody(user)));
    }

    /** Loads a user from user_create.json and fills in {{uid}} so every run is unique. */
    private UserCreate userBody(String user) {
        UserCreate data = TestDataLoader.getUserCreateData(user);
        String uid = String.valueOf(System.currentTimeMillis());

        UserCreate body = new UserCreate();
        body.setName(data.getName().replace("{{uid}}", uid));
        body.setEmail(data.getEmail().replace("{{uid}}", uid));
        return body;
    }

    /** The user created earlier in this run; falls back to a known existing user. */
    private String createdUserId() {
        String userId = getContext(USER_ID.name());
        return userId != null ? userId : "54";
    }

    private void send(String method, String endpoint, String userId, String body) {
        Map<String, String> pathParams = userId == null ? null : Map.of("userid", userId);
        Response response = requestWithPathParams(method, null, getParameterProperties(endpoint),
                pathParams, null, body);
        setContext(HTTP_RESPONSE.name(), response);
    }
}
