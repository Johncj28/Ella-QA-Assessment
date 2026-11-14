package starter.utils.model.requestModel.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** Products API request body. Fields left null (e.g. status) are not sent. */
@Data
//@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductCreate {
    // Test-data key only; read from JSON but never sent to the API
//    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String id;
    private String name;
    private Double price;
    private Integer quantity;
    private String status;
}
