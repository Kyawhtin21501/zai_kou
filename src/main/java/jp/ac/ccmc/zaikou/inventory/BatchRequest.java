package jp.ac.ccmc.zaikou.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record BatchRequest(@NotEmpty @Size(max = 100) List<@NotNull @Valid Reference> items) {
    public record Reference(@NotNull UUID id, @NotNull @PositiveOrZero Long version) {}
}
