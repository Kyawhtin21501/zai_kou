package jp.ac.ccmc.zaikou.inventory;

import java.time.Instant;
import java.util.UUID;

public record Item(UUID id, String sku, String name, String detail, String category,
                   int quantity, int minimum, int price, String location, long version, Instant updatedAt) {}
