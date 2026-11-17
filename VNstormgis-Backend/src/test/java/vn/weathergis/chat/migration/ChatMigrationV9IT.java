package vn.weathergis.chat.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.weathergis.chat.service.ChatTokenService;

/**
 * V8→V9 upgrade rehearsal on a DEDICATED PostGIS container (contract §3.2 step 4, §5.2) — never the
 * shared IT singleton. Proves the destructive D2 migration on legacy data and on empty tables:
 * backfill hash parity Java↔SQL (including base64url '-'/'_'), session_token dropped, is_fallback
 * default FALSE, unique index enforced, and V5 FK/index/trigger preserved.
 */
class ChatMigrationV9IT {

    private static final DockerImageName POSTGIS_IMAGE =
            DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                    .asCompatibleSubstituteFor("postgres");

    private final ChatTokenService tokenService = new ChatTokenService();

    private PostgreSQLContainer<?> newContainer() {
        PostgreSQLContainer<?> container =
                new PostgreSQLContainer<>(POSTGIS_IMAGE)
                        .withDatabaseName("v9_upgrade_db")
                        .withUsername("v9_user")
                        .withPassword("v9_password");
        container.start();
        return container;
    }

    private void migrateTo(PostgreSQLContainer<?> container, String target) {
        org.flywaydb.core.Flyway.configure()
                .dataSource(
                        container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .target(target)
                .load()
                .migrate();
    }

    private boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, "public", table, column)) {
            return rs.next();
        }
    }

    @Test
    void upgradeBackfillsLegacyTokensWithParityHashAndDropsRawColumn() throws Exception {
        try (PostgreSQLContainer<?> container = newContainer()) {
            migrateTo(container, "8");
            String rawToken = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmno-_";
            assertThat(rawToken).hasSize(43);
            UUID sessionId = UUID.randomUUID();
            UUID messageId = UUID.randomUUID();
            try (Connection conn =
                            DriverManager.getConnection(
                                    container.getJdbcUrl(),
                                    container.getUsername(),
                                    container.getPassword());
                    Statement st = conn.createStatement()) {
                st.executeUpdate(
                        "INSERT INTO chat_sessions (id, session_token, title) VALUES ('"
                                + sessionId
                                + "', '"
                                + rawToken
                                + "', 'Hội thoại mới')");
                st.executeUpdate(
                        "INSERT INTO chat_messages (id, session_id, sender_type, content) VALUES ('"
                                + messageId
                                + "', '"
                                + sessionId
                                + "', 'user', 'câu hỏi legacy')");
            }

            migrateTo(container, "9");

            try (Connection conn =
                            DriverManager.getConnection(
                                    container.getJdbcUrl(),
                                    container.getUsername(),
                                    container.getPassword());
                    Statement st = conn.createStatement()) {
                String expectedHash = tokenService.hash(rawToken);
                try (ResultSet rs =
                        st.executeQuery(
                                "SELECT token_hash FROM chat_sessions WHERE id = '"
                                        + sessionId
                                        + "'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo(expectedHash).hasSize(64);
                }
                assertThat(columnExists(conn, "chat_sessions", "session_token")).isFalse();
                assertThat(columnExists(conn, "chat_sessions", "token_hash")).isTrue();
                try (ResultSet rs =
                        st.executeQuery(
                                "SELECT is_fallback FROM chat_messages WHERE id = '"
                                        + messageId
                                        + "'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean(1)).isFalse();
                }
                // unique index enforced
                assertThatThrownBy(
                                () ->
                                        st.executeUpdate(
                                                "INSERT INTO chat_sessions (id, token_hash, title) VALUES ('"
                                                        + UUID.randomUUID()
                                                        + "', '"
                                                        + expectedHash
                                                        + "', 'trùng')"))
                        .isInstanceOf(SQLException.class);
                // V5 FK cascade preserved
                st.executeUpdate("DELETE FROM chat_sessions WHERE id = '" + sessionId + "'");
                try (ResultSet rs =
                        st.executeQuery(
                                "SELECT count(*) FROM chat_messages WHERE id = '"
                                        + messageId
                                        + "'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isZero();
                }
            }
        }
    }

    @Test
    void upgradeOnEmptyTablesSucceedsAndKeepsV5IndexAndTrigger() throws Exception {
        try (PostgreSQLContainer<?> container = newContainer()) {
            migrateTo(container, "8");
            migrateTo(container, "9");

            try (Connection conn =
                            DriverManager.getConnection(
                                    container.getJdbcUrl(),
                                    container.getUsername(),
                                    container.getPassword());
                    Statement st = conn.createStatement()) {
                assertThat(columnExists(conn, "chat_sessions", "session_token")).isFalse();
                assertThat(columnExists(conn, "chat_sessions", "token_hash")).isTrue();
                assertThat(columnExists(conn, "chat_messages", "is_fallback")).isTrue();

                try (ResultSet rs =
                        st.executeQuery(
                                "SELECT indexname FROM pg_indexes WHERE tablename = 'chat_messages' AND indexname = 'idx_chat_messages_session_time'")) {
                    assertThat(rs.next()).isTrue();
                }
                // NOT NULL on token_hash
                assertThatThrownBy(
                                () ->
                                        st.executeUpdate(
                                                "INSERT INTO chat_sessions (id, title) VALUES ('"
                                                        + UUID.randomUUID()
                                                        + "', 'x')"))
                        .isInstanceOf(SQLException.class);
                // updated_at trigger still fires
                UUID sessionId = UUID.randomUUID();
                st.executeUpdate(
                        "INSERT INTO chat_sessions (id, token_hash, title) VALUES ('"
                                + sessionId
                                + "', '"
                                + tokenService.hash("trigger-token")
                                + "', 'x')");
                try (ResultSet rs =
                        st.executeQuery(
                                "SELECT updated_at FROM chat_sessions WHERE id = '"
                                        + sessionId
                                        + "'")) {
                    assertThat(rs.next()).isTrue();
                    java.sql.Timestamp before = rs.getTimestamp(1);
                    Thread.sleep(20);
                    st.executeUpdate(
                            "UPDATE chat_sessions SET title = 'y' WHERE id = '" + sessionId + "'");
                    try (ResultSet rs2 =
                            st.executeQuery(
                                    "SELECT updated_at FROM chat_sessions WHERE id = '"
                                            + sessionId
                                            + "'")) {
                        assertThat(rs2.next()).isTrue();
                        assertThat(rs2.getTimestamp(1)).isAfter(before);
                    }
                }
            }
        }
    }
}
