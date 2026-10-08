package jp.ac.ccmc.zaikou.inventory;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record ItemRequest(
    @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{0,39}") String sku,
    @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}]*") String name,
    @NotNull @Size(max = 180) @Pattern(regexp = "[^\\p{Cntrl}]*") String detail,
    @NotNull @Pattern(regexp = "アルバム|フォトカード|ライブグッズ|アパレル|その他") String category,
    @NotNull @Min(0) @Max(99999999) Integer quantity,
    @NotNull @Min(0) @Max(99999999) Integer minimum,
    @NotNull @Min(0) @Max(99999999) Integer price,
    @NotNull @Size(max = 40) @Pattern(regexp = "[^\\p{Cntrl}]*") String location,
    @PositiveOrZero Long version
) {
    public ItemRequest {
        sku = sku == null ? null : sku.strip().toUpperCase(Locale.ROOT);
        name = name == null ? null : name.strip();
        detail = detail == null ? "" : detail.strip();
        location = location == null ? "" : location.strip();
    }
}
