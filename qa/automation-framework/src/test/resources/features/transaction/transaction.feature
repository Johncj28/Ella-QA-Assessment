Feature: Transactions API
  Mirrors the "03 Transactions" folder of the Ella API - QA Test Suite Postman collection.
  Every scenario gets its own fresh user and product (10 units in stock), so scenarios
  are independent and can run in any order.

  Background:
    Given a registered user and a product with 10 units in stock

  Scenario: 1. Successfully create a transaction (buy 2)
    When the user sends "TRANSACTION_CREATE_URL" request with "tx_buy_2" to buy the product
    Then the response status code should be 201
    And the response should contain a field named "statusCode" with the value "201"
    And the response should contain a field named "message" with the value "Transaction created successfully"
    And the transaction should be linked to the user and product with quantity 2

  Scenario: 2. Successfully get transaction by ID
    Given the user has bought the product with "tx_buy_2"
    When the user sends "TRANSACTION_GET_BY_ID_URL" request to get the created transaction by id
    Then the response status code should be 200
    And the response should contain a field named "message" with the value "Transaction retrieved successfully"
    And the response should return the created transaction

  Scenario: 3. Successfully get all existing transactions
    Given the user has bought the product with "tx_buy_2"
    When the user sends "TRANSACTION_GET_ALL_URL" request to get all existing transactions
    Then the response status code should be 200
    And the response should contain a field named "message" with the value "Transactions retrieved successfully"
    And the transaction list should contain the created transaction

  Scenario: 4. Buy more units than are in stock
    When the user sends "TRANSACTION_CREATE_URL" request with "tx_exceeds_stock" to buy the product
    Then the response status code should be 409
    And the response message should contain "exceeds available stock"

  Scenario: 5. Buy with a user that does not exist
    When the user sends "TRANSACTION_CREATE_URL" request with "tx_unknown_user" to buy the product
    Then the response status code should be 404
    And the response should contain a field named "message" with the value "User with ID 2000000000 not found"

  Scenario: 6. Buy a product that does not exist
    When the user sends "TRANSACTION_CREATE_URL" request with "tx_unknown_product" to buy the product
    Then the response status code should be 404
    And the response should contain a field named "message" with the value "Product with ID 2000000000 not found"

  Scenario: 7. Get a transaction that does not exist
    When the user sends "TRANSACTION_GET_BY_ID_URL" request to get the transaction with id "2000000000"
    Then the response status code should be 404
    And the response should contain a field named "message" with the value "Transaction with ID 2000000000 not found"

  Scenario Outline: 8. Create transaction with an invalid body - <case>
    When the user sends "TRANSACTION_CREATE_URL" transaction request with the following body
      """
      <body>
      """
    Then the response status code should be 400
    And the response should contain a field named "statusCode" with the value "400"

    Examples:
      | case               | body                                                                  |
      | quantity 0         | {"userId": {{userId}}, "productId": {{productId}}, "quantity": 0}     |
      | quantity -1        | {"userId": {{userId}}, "productId": {{productId}}, "quantity": -1}    |
      | quantity 1.5       | {"userId": {{userId}}, "productId": {{productId}}, "quantity": 1.5}   |
      | quantity "abc"     | {"userId": {{userId}}, "productId": {{productId}}, "quantity": "abc"} |
      | userId not numeric | {"userId": "abc", "productId": {{productId}}, "quantity": 1}          |

  Scenario: 9. Create transaction with an empty body lists every missing field
    When the user sends "TRANSACTION_CREATE_URL" transaction request with the following body
      """
      {}
      """
    Then the response status code should be 400
    And the response message should contain "userId"
    And the response message should contain "productId"
    And the response message should contain "quantity"

  # ---- Known bugs (see qa/BUGS.md in the Ella repo). Skipped by the "not @ignore" filter in CucumberTestSuite.
  # ---- Remove @ignore once the bug is fixed so the scenario becomes a regression check.

  @known-bug @ignore
  Scenario: BUG-01. Buying reduces the product's stock
    When the user sends "TRANSACTION_CREATE_URL" request with "tx_buy_2" to buy the product
    Then the response status code should be 201
    And the product stock should be 8

  @known-bug @ignore
  Scenario: Selling the last unit marks the product OUT_OF_STOCK
    When the user sends "TRANSACTION_CREATE_URL" request with "tx_buy_all_stock" to buy the product
    Then the response status code should be 201
    And the product stock should be 0
    And the product status should be "OUT_OF_STOCK"
