package quality.platform.lab.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookDetails {
    private String about;
    private Integer review;
    private Integer pages;
    private Double edition;
}
