Feature: Products API
  Mirrors the "02 Products" folder of the Ella API - QA Test Suite Postman collection.
  Scenarios run in order: later ones reuse the product created in scenario 1.

  Scenario: 1. Successfully create a new product
    When the user sends "PRODUCT_CREATE_URL" request with "product_01" to create a new product
    Then the response status code should be 201
    And the response should contain a field named "message" with the value "Product created successfully"
    And the response should contain a field named "data.status" with the value "FOR_SALE"
    And the response should contain a field named "data.price" with the value "49.99"
    And the response should contain a field named "data.quantity" with the value "10"

  Scenario: 2. Create a product with OUT_OF_STOCK status and quantity 0
    When the user sends "PRODUCT_CREATE_URL" request with "product_out_of_stock" to create another product
    Then the response status code should be 201
    And the response should contain a field named "data.status" with the value "OUT_OF_STOCK"

  Scenario: 3. Successfully get product by ID
    When the user sends "PRODUCT_GET_BY_ID_URL" request to get the created product by id
    Then the response status code should be 200
    And the response should contain a field named "message" with the value "Product retrieved successfully"
    And the response should return the created product

  Scenario: 4. Successfully get all existing products
    When the user sends "PRODUCT_GET_ALL_URL" request to get all existing products
    Then the response status code should be 200
    And the response should contain a field named "message" with the value "Products retrieved successfully"
    And the product list should contain the created product

  Scenario: 5. Successfully update the product's name and quantity
    When the user sends "PRODUCT_UPDATE_URL" request with "product_update" to update the created product
    Then the response status code should be 200
    And the response should contain a field named "message" with the value "Product updated successfully"
    And the response should contain the submitted product name
    And the response should contain a field named "data.quantity" with the value "25"

  Scenario: 6. Create a product with a name that already exists
    When the user sends "PRODUCT_CREATE_URL" request to create a product with the existing product's name
    Then the response status code should be 409
    And the response should contain a field named "message" with the value "Product name already exists"

  Scenario: 7. Get a product that does not exist
    When the user sends "PRODUCT_GET_BY_ID_URL" request to get the product with id "2000000000"
    Then the response status code should be 404

  Scenario: 8. Update a product that does not exist
    When the user sends "PRODUCT_UPDATE_URL" request with "product_ghost" to update the product with id "2000000000"
    Then the response status code should be 404

  Scenario Outline: 9. Create product with an invalid body - <case>
    When the user sends "PRODUCT_CREATE_URL" request with the following body
      """
      <body>
      """
    Then the response status code should be 400
    And the response should contain a field named "statusCode" with the value "400"

    Examples:
      | case                 | body                                                                 |
      | missing name         | {"price": 1, "quantity": 1}                                          |
      | missing price        | {"name": "x {{uid}}", "quantity": 1}                                 |
      | missing quantity     | {"name": "x {{uid}}", "price": 1}                                    |
      | price is a string    | {"name": "x {{uid}}", "price": "ten", "quantity": 1}                 |
      | quantity -1          | {"name": "x {{uid}}", "price": 1, "quantity": -1}                    |

  Scenario: 10. Create product with a status not in the enum
    When the user sends "PRODUCT_CREATE_URL" request with the following body
      """
      {"name": "x {{uid}}", "price": 1, "quantity": 1, "status": "DISCONTINUED"}
      """
    Then the response status code should be 400
    And the response should contain a field named "statusCode" with the value "400"
    And the validation message should mention "valid Product Status"
