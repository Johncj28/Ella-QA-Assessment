package starter.utils.model.requestModel.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Transactions API request body.
 * In test data, a null userId / productId means "use the user / product created for this scenario".
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionCreate {
    // Test-data key only; read from JSON but never sent to the API
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String id;
    private Integer userId;
    private Integer productId;
    private Integer quantity;
}
