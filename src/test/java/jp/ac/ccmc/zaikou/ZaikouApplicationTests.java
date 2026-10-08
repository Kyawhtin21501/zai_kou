package jp.ac.ccmc.zaikou;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"app.admin.username=admin", "app.admin.password=test-only-password-123456"})
@AutoConfigureMockMvc
class ZaikouApplicationTests {
    static final String TEST_URL = System.getenv("ZAIKOU_TEST_DATABASE_URL");
    static final PostgreSQLContainer DB;
    static {
        if (TEST_URL == null) {
            DB = new PostgreSQLContainer("postgres:18-alpine");
            DB.start();
        } else {
            if (!TEST_URL.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/zaikou_test"))
                throw new IllegalArgumentException("Only a disposable local zaikou_test database may be used");
            DB = null;
        }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> DB == null ? TEST_URL : DB.getJdbcUrl());
        properties.add("spring.datasource.username", () -> DB == null ? "testuser" : DB.getUsername());
        properties.add("spring.datasource.password", () -> DB == null ? "" : DB.getPassword());
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @BeforeEach void clean() { jdbc.execute("TRUNCATE inventory_audit, inventory_item RESTART IDENTITY"); }
    private String item(String sku, int quantity, Long version) throws Exception {
        var values = new java.util.HashMap<String, Object>(Map.of("sku",sku,"name","テスト商品","detail","", "category","アルバム","quantity",quantity,"minimum",5,"price",1000,"location","A-01"));
        if (version != null) values.put("version", version);
        return mapper.writeValueAsString(values);
    }
    private tools.jackson.databind.JsonNode create(String sku) throws Exception {
        var result = mvc.perform(post("/api/items").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(item(sku, 10, null)))
            .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }
    private String refs(tools.jackson.databind.JsonNode item) throws Exception {
        return mapper.writeValueAsString(Map.of("items",List.of(Map.of("id",item.get("id").asString(),"version",item.get("version").asLong()))));
    }
    @Test void authenticationAuthorizationCsrfAndHealth() throws Exception {
        mvc.perform(get("/api/items")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/items").with(user("reader").roles("VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/items").with(user("admin").roles("ADMIN")).contentType("application/json").content(item("AA",1,null))).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/items").header("Origin","https://attacker.example")).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
    @Test void realLoginCsrfRotationLogout() throws Exception {
        var tokenResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) tokenResult.getRequest().getSession();
        var token = mapper.readTree(tokenResult.getResponse().getContentAsString());
        mvc.perform(post("/api/auth/login").session(session).header(token.get("headerName").asString(), token.get("token").asString())
            .param("username","admin").param("password","wrong")).andExpect(status().isUnauthorized());
        var login = mvc.perform(post("/api/auth/login").session(session).header(token.get("headerName").asString(), token.get("token").asString())
            .param("username","admin").param("password","test-only-password-123456")).andExpect(status().isNoContent()).andReturn();
        session = (MockHttpSession) login.getRequest().getSession();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("admin"));
        mvc.perform(post("/api/items").session(session).header(token.get("headerName").asString(), token.get("token").asString()).contentType("application/json").content(item("AA",1,null))).andExpect(status().isForbidden());
        var newToken = mapper.readTree(mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/auth/logout").session(session).header(newToken.get("headerName").asString(),newToken.get("token").asString())).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test void crudConflictDeleteRestoreAndAudit() throws Exception {
        var created = create(" test-001 ");
        assertThat(created.get("sku").asString()).isEqualTo("TEST-001");
        String id = created.get("id").asString();
        mvc.perform(put("/api/items/"+id).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(item("TEST-001",0,0L))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put("/api/items/"+id).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(item("TEST-001",2,0L))).andExpect(status().isConflict());
        var deleted = mvc.perform(post("/api/items/delete").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"items\":[{\"id\":\""+id+"\",\"version\":1}]}"))
            .andExpect(status().isOk()).andReturn();
        var reference = mapper.readTree(deleted.getResponse().getContentAsString()).get(0);
        mvc.perform(get("/api/items").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$.total").value(0));
        mvc.perform(post("/api/items/restore").with(user("another").roles("ADMIN")).with(csrf()).contentType("application/json").content(refs(reference))).andExpect(status().isConflict());
        mvc.perform(post("/api/items/restore").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(refs(reference))).andExpect(status().isOk());
        mvc.perform(get("/api/items?status=out").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.summary.out").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_audit", Long.class)).isEqualTo(4);
    }
    @Test void rejectsInvalidInputAndDuplicateSku() throws Exception {
        create("DUP");
        for (String body : List.of(item("bad sku",1,null), item("NEG",-1,null), item("OVER",100000000,null), item("AA",1,null).replace("テスト商品"," "), item("AA",1,null).replace("アルバム","INVALID"), item("AA",1,null).replace("\"quantity\":1", "\"quantity\":1.5"), item("AA",1,null).replace("\"name\":", "\"unexpected\":true,\"name\":"))) {
            mvc.perform(post("/api/items").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/items").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(item("dup",1,null))).andExpect(status().isConflict());
        mvc.perform(get("/api/items?size=101").with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/items?sort=name;DROP%20TABLE%20inventory_item").with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/items/"+UUID.randomUUID()).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(item("AA",1,0L))).andExpect(status().isNotFound());
    }
    @Test void bulkIsAtomicAndSearchIsLiteral() throws Exception {
        var first = create("FIRST"); create("SECOND");
        String batch = "{\"items\":[{\"id\":\""+first.get("id").asString()+"\",\"version\":0},{\"id\":\""+UUID.randomUUID()+"\",\"version\":0}]}";
        mvc.perform(post("/api/items/delete").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(batch)).andExpect(status().isNotFound());
        mvc.perform(get("/api/items?size=1&page=1").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.summary.totalValue").value("20000"));
        mvc.perform(get("/api/items").param("query","' OR 1=1 --").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$.total").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_item WHERE deleted_at IS NULL", Long.class)).isEqualTo(2);
    }
    @Test void csvNeutralizesFormulaAndBodyLimit() throws Exception {
        mvc.perform(post("/api/items").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(item("CSV",1,null).replace("テスト商品","=1+1"))).andExpect(status().isCreated());
        mvc.perform(get("/api/items/export").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("\"'=1+1\"")));
        mvc.perform(post("/api/items").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("x".repeat(17000))).andExpect(status().isPayloadTooLarge());
    }
    @Test void concurrentUpdatesCannotOverwriteEachOther() throws Exception {
        var created = create("RACE");
        String id = created.get("id").asString();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> operation = () -> {
                start.await();
                return mvc.perform(put("/api/items/" + id).with(user("admin").roles("ADMIN")).with(csrf())
                    .contentType("application/json").content(item("RACE",2,0L))).andReturn().getResponse().getStatus();
            };
            var one = pool.submit(operation); var two = pool.submit(operation); start.countDown();
            assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder(200,409);
        }
        assertThat(jdbc.queryForObject("SELECT version FROM inventory_item WHERE sku='RACE'", Long.class)).isEqualTo(1);
    }
    @Test void restoreExpiryDuplicateReferencesAndMissingVersion() throws Exception {
        var created = create("RESTORE");
        var id = created.get("id").asString();
        mvc.perform(put("/api/items/" + id).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(item("RESTORE",1,null))).andExpect(status().isBadRequest());
        String duplicated = mapper.writeValueAsString(Map.of("items", List.of(Map.of("id", id, "version", 0), Map.of("id", id, "version", 0))));
        mvc.perform(post("/api/items/delete").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(duplicated)).andExpect(status().isBadRequest());
        var result = mvc.perform(post("/api/items/delete").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(refs(created))).andExpect(status().isOk()).andReturn();
        jdbc.update("UPDATE inventory_item SET deleted_at=CURRENT_TIMESTAMP - INTERVAL '11 minutes'");
        mvc.perform(post("/api/items/restore").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(refs(mapper.readTree(result.getResponse().getContentAsString()).get(0)))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_item WHERE deleted_at IS NULL", Long.class)).isZero();
    }

}
