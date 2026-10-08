package jp.ac.ccmc.zaikou.inventory;

import jp.ac.ccmc.zaikou.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service
public class InventoryService {
    private final NamedParameterJdbcTemplate jdbc;
    private static final RowMapper<Item> MAPPER = (rs, n) -> new Item(
        rs.getObject("id", UUID.class), rs.getString("sku"), rs.getString("name"), rs.getString("detail"),
        rs.getString("category"), rs.getInt("quantity"), rs.getInt("minimum"), rs.getInt("price"),
        rs.getString("location"), rs.getLong("version"), rs.getTimestamp("updated_at").toInstant());
    public static final List<String> CATEGORIES = List.of("アルバム", "フォトカード", "ライブグッズ", "アパレル", "その他");
    public record Summary(long itemCount, long totalQuantity, String totalValue, long low, long out, Map<String, Long> categories) {}
    public record Page(List<Item> items, long total, int page, int size, Summary summary) {}
    private record Filter(String where, Map<String, Object> params, String order) {}
    public InventoryService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private Filter filter(String query, String category, String status, String sort) {
        var params = new HashMap<String, Object>();
        String where = " WHERE deleted_at IS NULL";
        if (!query.isBlank()) {
            where += " AND position(:query in lower(name || ' ' || sku || ' ' || detail)) > 0";
            params.put("query", query.strip().toLowerCase(Locale.ROOT));
        }
        if (!category.isBlank()) {
            if (!CATEGORIES.contains(category)) throw new ApiException(HttpStatus.BAD_REQUEST, "カテゴリーが正しくありません。");
            where += " AND category = :category";
            params.put("category", category);
        }
        where += switch (status) {
            case "all" -> "";
            case "ok" -> " AND quantity > minimum";
            case "low" -> " AND quantity > 0 AND quantity <= minimum";
            case "out" -> " AND quantity = 0";
            case "attention" -> " AND quantity <= minimum";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "ステータスが正しくありません。");
        };
        String order = switch (sort) {
            case "updated" -> "updated_at DESC, id";
            case "quantity" -> "quantity ASC, id";
            case "name" -> "name ASC, id";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "並び順が正しくありません。");
        };
        return new Filter(where, params, order);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page list(String query, String category, String status, String sort, int page, int size) {
        Filter f = filter(query, category, status, sort);
        long total = jdbc.queryForObject("SELECT count(*) FROM inventory_item" + f.where, f.params, Long.class);
        int actualPage = (int) Math.min(page, Math.max(0, (total - 1) / size));
        f.params.put("limit", size);
        f.params.put("offset", (long) actualPage * size);
        var items = jdbc.query("SELECT * FROM inventory_item" + f.where + " ORDER BY " + f.order + " LIMIT :limit OFFSET :offset", f.params, MAPPER);
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        CATEGORIES.forEach(c -> categoryCounts.put(c, 0L));
        jdbc.query("SELECT category, count(*) AS amount FROM inventory_item WHERE deleted_at IS NULL GROUP BY category", Map.of(),
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> categoryCounts.put(rs.getString("category"), rs.getLong("amount")));
        Summary summary = jdbc.queryForObject("""
            SELECT count(*) AS amount, coalesce(sum(quantity),0) AS quantity,
                coalesce(sum(quantity::numeric * price),0)::text AS value,
                count(*) FILTER (WHERE quantity > 0 AND quantity <= minimum) AS low,
                count(*) FILTER (WHERE quantity = 0) AS out
            FROM inventory_item WHERE deleted_at IS NULL
            """, Map.of(), (rs, n) -> new Summary(rs.getLong("amount"), rs.getLong("quantity"), rs.getString("value"), rs.getLong("low"), rs.getLong("out"), categoryCounts));
        return new Page(items, total, actualPage, size, summary);
    }

    private Map<String, Object> parameters(ItemRequest r) {
        var p = new HashMap<String, Object>();
        p.put("sku", r.sku()); p.put("name", r.name()); p.put("detail", r.detail()); p.put("category", r.category());
        p.put("quantity", r.quantity()); p.put("minimum", r.minimum()); p.put("price", r.price()); p.put("location", r.location());
        return p;
    }
    @Transactional
    public Item create(ItemRequest r, String actor) {
        if (r.version() != null) throw new ApiException(HttpStatus.BAD_REQUEST, "新規登録には version を指定できません。");
        var p = parameters(r); p.put("id", UUID.randomUUID());
        Item item = jdbc.queryForObject("""
            INSERT INTO inventory_item(id,sku,name,detail,category,quantity,minimum,price,location)
            VALUES (:id,:sku,:name,:detail,:category,:quantity,:minimum,:price,:location) RETURNING *
            """, p, MAPPER);
        audit(item, actor, "CREATE");
        return item;
    }
    @Transactional
    public Item update(UUID id, ItemRequest r, String actor) {
        if (r.version() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "更新には version が必要です。");
        var p = parameters(r); p.put("id", id); p.put("version", r.version());
        var result = jdbc.query("""
            UPDATE inventory_item SET sku=:sku,name=:name,detail=:detail,category=:category,
                quantity=:quantity,minimum=:minimum,price=:price,location=:location,
                version=version+1,updated_at=CURRENT_TIMESTAMP
            WHERE id=:id AND version=:version AND deleted_at IS NULL RETURNING *
            """, p, MAPPER);
        Item item = changed(result, id);
        audit(item, actor, "UPDATE");
        return item;
    }
    @Transactional
    public List<Item> changeDeleted(BatchRequest request, String actor, boolean restore) {
        if (request.items().stream().map(BatchRequest.Reference::id).distinct().count() != request.items().size())
            throw new ApiException(HttpStatus.BAD_REQUEST, "同じ商品を複数回指定できません。");
        List<Item> changed = new ArrayList<>();
        // Stable lock order avoids deadlocks for overlapping bulk requests.
        for (var ref : request.items().stream().sorted(Comparator.comparing(BatchRequest.Reference::id)).toList()) {
            String sql = restore ? """
                UPDATE inventory_item SET deleted_at=NULL,deleted_by=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND version=:version AND deleted_at > CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                AND deleted_by=:actor RETURNING *
                """ : """
                UPDATE inventory_item SET deleted_at=CURRENT_TIMESTAMP,deleted_by=:actor,version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND version=:version AND deleted_at IS NULL RETURNING *
                """;
            Item item = changed(jdbc.query(sql, Map.of("id", ref.id(), "version", ref.version(), "actor", actor), MAPPER), ref.id());
            audit(item, actor, restore ? "RESTORE" : "DELETE");
            changed.add(item);
        }
        return changed;
    }
    private Item changed(List<Item> result, UUID id) {
        if (!result.isEmpty()) return result.getFirst();
        Long exists = jdbc.queryForObject("SELECT count(*) FROM inventory_item WHERE id=:id", Map.of("id", id), Long.class);
        if (exists == 0) throw new ApiException(HttpStatus.NOT_FOUND, "商品が見つかりません。");
        throw new ApiException(HttpStatus.CONFLICT, "他の操作で変更されたか、復元期限を過ぎています。一覧を更新して確認してください。");
    }
    private void audit(Item item, String actor, String action) {
        jdbc.update("INSERT INTO inventory_audit(item_id,actor,action,version) VALUES (:id,:actor,:action,:version)",
            Map.of("id", item.id(), "actor", actor, "action", action, "version", item.version()));
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public String export(String query, String category, String status, String sort) {
        Filter f = filter(query, category, status, sort);
        long total = jdbc.queryForObject("SELECT count(*) FROM inventory_item" + f.where, f.params, Long.class);
        if (total > 10000) throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "CSV は一度に10,000件までです。絞り込んでください。");
        var items = jdbc.query("SELECT * FROM inventory_item" + f.where + " ORDER BY " + f.order + " LIMIT 10000", f.params, MAPPER);
        StringBuilder csv = new StringBuilder("\uFEFF商品コード,商品名,カテゴリー,在庫数,単価,保管場所\r\n");
        for (Item item : items) csv.append(String.join(",", cell(item.sku()), cell(item.name()), cell(item.category()), cell(item.quantity()), cell(item.price()), cell(item.location()))).append("\r\n");
        return csv.toString();
    }
    private String cell(Object value) {
        String text = value.toString();
        if (text.matches("^[\\s]*[=+@-].*")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
