package starter.step_definitions.common;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;

import static starter.assertions.AssertionsAPI.*;

public class CommonStepDefinitions {

    @Then("the response status code should be {int}")
    public void responseShouldHaveStatusCode(int code) {
        checkResponseStatusCode(code);
    }

    @And("the response {string} should contain {string}")
    public void theResponseShouldContain(String path, String expected) {
        checkResponseContains(path, expected);
    }

    @And("the response should contain a field named {string} with the value {string}")
    public void responseShouldContainAFieldNamedWithValue(String jsonPath, String fieldValue) {
        checkResponseField(jsonPath, fieldValue);
    }

    @Then("the response {string} should contain exactly {int} items")
    public void verifyExactItemCount(String jsonPath, int count) {
        checkResponseSize(jsonPath, count);
    }

    @Then("the response status code should be {int} or {int}")
    public void theResponseStatusCodeShouldBeOr(int code1, int code2) {
        checkResponseStatusCodes(code1, code2);
    }

    @Then("the response status code should be either {int} or {int}")
    public void theResponseStatusCodeShouldBeEitherOr(int code1, int code2) {
        checkResponseStatusCodeEither(code1, code2);
    }

    @And("the response message should be one of the following")
    public void theResponseMessageShouldBeOneOfTheFollowing(DataTable table) {
        validateResponseMessageIsOneOf(table);
    }
}
