package jp.ac.ccmc.zaikou.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/items")
public class InventoryController {
    private final InventoryService service;
    public InventoryController(InventoryService service) { this.service = service; }
    @GetMapping
    InventoryService.Page list(@RequestParam(defaultValue="") @Size(max=100) String query,
        @RequestParam(defaultValue="") @Size(max=20) String category,
        @RequestParam(defaultValue="all") String status, @RequestParam(defaultValue="updated") String sort,
        @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.list(query, category, status, sort, page, size);
    }
    @PostMapping
    ResponseEntity<Item> create(@Valid @RequestBody ItemRequest request, Principal principal) {
        Item item = service.create(request, principal.getName());
        return ResponseEntity.created(URI.create("/api/items/" + item.id())).body(item);
    }
    @PutMapping("/{id}")
    Item update(@PathVariable UUID id, @Valid @RequestBody ItemRequest request, Principal principal) {
        return service.update(id, request, principal.getName());
    }
    @PostMapping("/delete")
    List<Item> delete(@Valid @RequestBody BatchRequest request, Principal principal) {
        return service.changeDeleted(request, principal.getName(), false);
    }
    @PostMapping("/restore")
    List<Item> restore(@Valid @RequestBody BatchRequest request, Principal principal) {
        return service.changeDeleted(request, principal.getName(), true);
    }
    @GetMapping(value="/export", produces="text/csv;charset=UTF-8")
    ResponseEntity<String> export(@RequestParam(defaultValue="") @Size(max=100) String query,
        @RequestParam(defaultValue="") @Size(max=20) String category,
        @RequestParam(defaultValue="all") String status, @RequestParam(defaultValue="updated") String sort) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=zaikoo-inventory.csv")
            .body(service.export(query, category, status, sort));
    }
}
