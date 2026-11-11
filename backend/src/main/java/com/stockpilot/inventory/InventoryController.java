package com.stockpilot.inventory;

import com.stockpilot.inventory.InventoryDtos.MovementRequest;
import com.stockpilot.inventory.InventoryDtos.MovementResponse;
import com.stockpilot.inventory.InventoryDtos.ProductRequest;
import com.stockpilot.inventory.InventoryDtos.ProductResponse;
import com.stockpilot.inventory.InventoryDtos.Recommendation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class InventoryController {

    private final InventoryService inventory;
    private final DashboardService dashboard;

    public InventoryController(InventoryService inventory, DashboardService dashboard) {
        this.inventory = inventory;
        this.dashboard = dashboard;
    }

    @GetMapping("/products")
    public List<ProductResponse> products() {
        return inventory.listProducts();
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = inventory.createProduct(request);
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    @PutMapping("/products/{productId}")
    public ProductResponse updateProduct(@PathVariable UUID productId,
                                         @Valid @RequestBody ProductRequest request) {
        return inventory.updateProduct(productId, request);
    }

    @PostMapping("/stock-movements")
    public ResponseEntity<MovementResponse> recordMovement(@Valid @RequestBody MovementRequest request) {
        return ResponseEntity.status(201).body(inventory.recordMovement(request));
    }

    @GetMapping("/stock-movements")
    public org.springframework.data.domain.Page<MovementResponse> movementHistory(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) StockMovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Start date must not be after end date");
        }
        ZoneId zone = ZoneId.of("Europe/Belgrade");
        Instant fromTime = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant toTime = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        return inventory.movementHistory(productId, type, fromTime, toTime,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
    }

    @GetMapping(value = "/stock-movements/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportMovementHistory(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) StockMovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Start date must not be after end date");
        }
        ZoneId zone = ZoneId.of("Europe/Belgrade");
        Instant fromTime = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant toTime = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        StreamingResponseBody stream = output -> {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
            writer.write('\uFEFF');
            writeCsvRow(writer, "Vreme", "Proizvod", "ID proizvoda", "Vrsta", "Količina",
                    "Broj porudžbine", "Napomena");
            int currentPage = 0;
            Page<MovementResponse> movementsPage;
            do {
                movementsPage = inventory.movementHistory(productId, type, fromTime, toTime,
                        PageRequest.of(currentPage++, 500, Sort.by(Sort.Direction.DESC, "occurredAt")));
                for (MovementResponse movement : movementsPage.getContent()) {
                    writeCsvRow(writer, movement.occurredAt().toString(), movement.productName(),
                            movement.productId().toString(), movement.type().name(),
                            Integer.toString(movement.quantity()),
                            movement.purchaseOrderNumber() == null ? "" : movement.purchaseOrderNumber(),
                            movement.note() == null ? "" : movement.note());
                }
            } while (movementsPage.hasNext());
            writer.flush();
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"stockpilot-kretanja.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(stream);
    }

    @GetMapping("/replenishment-recommendations")
    public List<Recommendation> recommendations() {
        return inventory.recommendations();
    }

    @GetMapping("/dashboard")
    public com.stockpilot.inventory.InventoryDtos.DashboardResponse dashboard() {
        return dashboard.getDashboard();
    }

    private static void writeCsvRow(BufferedWriter writer, String... values) throws IOException {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) writer.write(',');
            String value = values[index];
            String significant = value.stripLeading();
            if (!significant.isEmpty() && "=+-@\t\r".indexOf(significant.charAt(0)) >= 0
                    && !value.matches("-?\\d+(\\.\\d+)?")) value = "'" + value;
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\r\n");
    }
}
