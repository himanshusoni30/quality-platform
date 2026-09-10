package quality.platform.lab.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class BookPatchRequest {
    private BigDecimal price;
    private BookDetails details;
}
