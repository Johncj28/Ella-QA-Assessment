Feature: Users API
  Mirrors the "01 Users" folder of the Ella API - QA Test Suite Postman collection.
  Scenarios run in order: later ones reuse the users created in scenarios 1 and 2.

  Scenario: 1.Successfully register a new user
    When the user sends "USER_CREATE_URL" request with "user_01" to create a new user
    Then the response status code should be 201
    And the response should contain a field named "message" with the value "User created successfully"

  Scenario: 2.Successfully register a second user
    When the user sends "USER_CREATE_URL" request with "user_02" to create a second user
    Then the response status code should be 201
    And the response should contain a field named "message" with the value "User created successfully"

  Scenario: 3. Successfully get user by ID
    When the user sends "USER_GET_BY_ID_URL" request to get existing user by id
    Then the response status code should be 200
    And the response should contain a field named "message" with the value "User retrieved successfully"

  Scenario: 4. Successfully get all existing users
    When the user sends "USER_GET_ALL_URL" request to get all existing users
    Then the response status code should be 200
    And the response should contain a field named "message" with the value "Users retrieved successfully"

  Scenario: 5. Successfully update the user's name and email
    When the user sends "USER_UPDATE_URL" request with "user_update" to update the created user
    Then the response status code should be 200
    And the response should contain a field named "message" with the value "User updated successfully"
    And the response should contain the submitted name and email

  Scenario: 6. Update user with an email that is already taken
    When the user sends "USER_UPDATE_URL" request to update the created user with the second user's email
    Then the response status code should be 409
    And the response should contain a field named "message" with the value "Email already exists"

  Scenario: 7. Update a user that does not exist
    When the user sends "USER_UPDATE_URL" request with "user_ghost" to update the user with id "2000000000"
    Then the response status code should be 404

  Scenario: 8. Get a user that does not exist
    When the user sends "USER_GET_BY_ID_URL" request to get the user with id "2000000000"
    Then the response status code should be 404
    And the response should contain a field named "message" with the value "User with ID 2000000000 not found"

  Scenario Outline: 9. Create user with an invalid body - <case>
    When the user sends "USER_CREATE_URL" request with the following body
      """
      <body>
      """
    Then the response status code should be 400
    And the response should contain a field named "statusCode" with the value "400"

    Examples:
      | case                | body                                                  |
      | missing name        | {"email": "noname.{{uid}}@example.test"}              |
      | missing email       | {"name": "No Email"}                                  |
      | name not a string   | {"name": 12345, "email": "num.{{uid}}@example.test"}  |
      | empty body          | {}                                                    |
