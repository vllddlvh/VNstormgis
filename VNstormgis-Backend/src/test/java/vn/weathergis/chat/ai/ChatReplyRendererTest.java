package vn.weathergis.chat.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.RepresentativeThresholdSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.chat.config.ChatReplyProperties;
import vn.weathergis.chat.context.ChatContextOutcome;
import vn.weathergis.chat.context.FactualContext;
import vn.weathergis.chat.context.NoDataReason;
import vn.weathergis.chat.intent.ChatIntent;

/** Deterministic Vietnamese renderer + honest templates (W5-04 §6, D-W5-04-01/02 proposals). */
class ChatReplyRendererTest {

    private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");
    private static final UUID ALERT_ID = UUID.fromString("00000000-0000-0000-0000-000000000504");

    private final ChatReplyProperties properties = new ChatReplyProperties();
    private final ChatReplyRenderer renderer = new ChatReplyRenderer(properties);

    private FactualContext.StationObservation station(
            String code, String name, Double rain, Double wind, Instant recordedAt, boolean stale) {
        return new FactualContext.StationObservation(
                code,
                name,
                List.of(108.2, 16.0),
                recordedAt,
                stale,
                null,
                null,
                null,
                wind,
                null,
                null,
                rain,
                null,
                "Rain",
                "mưa vừa",
                Map.of(
                        "rain_1h", "mm",
                        "rain_3h", "mm",
                        "wind_speed", "m/s",
                        "wind_gust", "m/s",
                        "temperature", "°C",
                        "humidity", "%",
                        "pressure", "hPa",
                        "wind_direction", "deg"));
    }

    private FactualContext context(
            ChatIntent intent,
            List<FactualContext.StationObservation> stations,
            List<FactualContext.StormSummary> storms,
            FactualContext.AlertContext alert) {
        return new FactualContext(
                FactualContext.SCHEMA_VERSION,
                intent,
                NOW,
                ChatContextOutcome.CONTEXT_READY,
                null,
                stations,
                storms,
                alert,
                new FactualContext.ReferencedEntities(List.of(), List.of(), List.of()),
                new FactualContext.ContextMeta(60, 0, 0, false),
                100);
    }

    private FactualContext rainFixture() {
        return context(
                ChatIntent.RAIN_TOP,
                List.of(
                        station(
                                "DNG",
                                "Đà Nẵng",
                                82.4,
                                5.0,
                                Instant.parse("2026-11-17T08:50:00Z"),
                                false),
                        station(
                                "SGN",
                                "TP. Hồ Chí Minh",
                                12.0,
                                3.0,
                                Instant.parse("2026-11-17T07:30:00Z"),
                                true)),
                List.of(),
                null);
    }

    private FactualContext.StormSummary storm(String id, boolean withForecast) {
        return new FactualContext.StormSummary(
                id,
                "Storm " + id,
                "Tropical Storm",
                45.0,
                960.0,
                List.of(127.4, 31.2),
                NOW.minusSeconds(600),
                "db_update",
                false,
                "GDACS",
                withForecast
                        ? new FactualContext.ForecastPoint(
                                NOW.plusSeconds(3600),
                                "forecast",
                                List.of(126.0, 30.0),
                                40.0,
                                48.0,
                                970.0,
                                "Tropical Storm")
                        : null,
                Map.of("max_wind_speed", "m/s", "min_pressure", "hPa", "wind_speed", "m/s"));
    }

    private FactualContext alertFixture(int violationCount, int renderedCount, String severity) {
        return alertFixture(violationCount, renderedCount, severity, 65.5, 50.0, "rain_1h", "mm");
    }

    private FactualContext alertFixture(
            int violationCount,
            int renderedCount,
            String severity,
            double measured,
            double threshold,
            String parameter,
            String unit) {
        List<ViolationSnapshot> violations = new ArrayList<>();
        for (int i = 0; i < violationCount; i++) {
            violations.add(
                    new ViolationSnapshot(
                            1,
                            parameter,
                            ">=",
                            threshold,
                            severity,
                            measured,
                            unit,
                            NOW.minusSeconds(600),
                            NOW.minusSeconds(300),
                            "K" + i,
                            null,
                            null,
                            null));
        }
        AlertFactualSnapshot snapshot =
                new AlertFactualSnapshot(
                        UUID.randomUUID(),
                        "DNG",
                        "Đà Nẵng",
                        List.of(108.2, 16.0),
                        UUID.randomUUID(),
                        NOW.minusSeconds(300),
                        new RepresentativeThresholdSnapshot(
                                1, parameter, ">=", threshold, severity, unit, "K0"),
                        violations.subList(0, renderedCount));
        FactualContext.AlertContext alert =
                new FactualContext.AlertContext(
                        ALERT_ID,
                        "active",
                        severity,
                        Instant.parse("2026-11-17T08:45:00Z"),
                        null,
                        true,
                        snapshot,
                        violations.subList(0, renderedCount).stream()
                                .map(
                                        v ->
                                                new FactualContext.ViolationOrder(
                                                        v.violationKey(), severity, 15.5))
                                .toList(),
                        false,
                        1000);
        return context(ChatIntent.ALERT_EXPLAIN, List.of(), List.of(), alert);
    }

    @Test
    void rainTopFullMatchesTheApprovedFixtureWording() {
        var rendered = renderer.renderBestEffort(rainFixture(), 0);

        assertThat(rendered.minimal()).isFalse();
        assertThat(rendered.variant()).isEqualTo(ChatReplyGuardrail.Variant.FULL);
        assertThat(rendered.text())
                .isEqualTo(
                        "Trạm Đà Nẵng (DNG) đang có lượng mưa 1 giờ cao nhất: 82.4 mm, ghi nhận lúc"
                                + " 08:50 UTC ngày 17/11/2026. Trạm TP. Hồ Chí Minh (SGN): 12 mm lúc"
                                + " 07:30 UTC ngày 17/11/2026 (số liệu cũ).");
    }

    @Test
    void measuredZeroIsRenderedAsZeroNotNullAndNullFieldsAreOmitted() {
        FactualContext ctx =
                context(
                        ChatIntent.STATION_CURRENT,
                        List.of(station("DNG", "Đà Nẵng", 0.0, 5.0, NOW.minusSeconds(600), false)),
                        List.of(),
                        null);

        String text = renderer.renderBestEffort(ctx, 0).text();

        assertThat(text).contains("0 mm").contains("5 m/s");
        assertThat(text)
                .doesNotContain("nhiệt độ")
                .doesNotContain("độ ẩm")
                .doesNotContain("áp suất");
    }

    @Test
    void windTopUsesWindSpeedWithCorrectUnit() {
        FactualContext ctx =
                context(
                        ChatIntent.WIND_TOP,
                        List.of(
                                station(
                                        "DNG",
                                        "Đà Nẵng",
                                        82.4,
                                        25.0,
                                        NOW.minusSeconds(600),
                                        false)),
                        List.of(),
                        null);

        String text = renderer.renderBestEffort(ctx, 0).text();

        assertThat(text).contains("tốc độ gió").contains("25 m/s").doesNotContain("82.4");
    }

    @Test
    void stormFullCarriesGdacsDbUpdateAndForecastLabels() {
        FactualContext ctx =
                context(ChatIntent.STORM_ACTIVE, List.of(), List.of(storm("W504TC", true)), null);

        String text = renderer.renderBestEffort(ctx, 0).text();

        assertThat(text)
                .contains("W504TC")
                .contains("GDACS")
                .contains("45 m/s")
                .contains("960 hPa")
                .contains("[127.4,31.2]")
                .contains("cơ sở dữ liệu")
                .contains("Dự báo gần nhất")
                .contains("dữ liệu dự báo, không phải số thực đo")
                .contains("40 m/s");
    }

    @Test
    void alertExplainFullMatchesTheApprovedFixtureWording() {
        FactualContext ctx = alertFixture(75, 1, "DANGER");

        String text = renderer.renderBestEffort(ctx, 74).text();

        assertThat(text)
                .contains(
                        "Cảnh báo DANGER tại trạm Đà Nẵng (DNG) phát lúc 08:45 UTC ngày 17/11/2026")
                .contains("lượng mưa 1 giờ đo được 65.5 mm")
                .contains("vượt ngưỡng >= 50 mm")
                .contains("chênh 15.5 mm")
                .contains("Và 74 vi phạm khác (không liệt kê chi tiết).");
    }

    @Test
    void alertStatusAndResolvedAtAreRenderedWithoutTtlInference() {
        FactualContext base = alertFixture(1, 1, "WARNING");
        FactualContext.AlertContext alert = base.alert();
        FactualContext resolved =
                context(
                        ChatIntent.ALERT_EXPLAIN,
                        List.of(),
                        List.of(),
                        new FactualContext.AlertContext(
                                alert.alertId(),
                                "resolved",
                                alert.severityLevel(),
                                alert.triggeredAt(),
                                NOW.minusSeconds(60),
                                true,
                                alert.factualSnapshot(),
                                alert.violationOrder(),
                                false,
                                alert.snapshotSizeBytes()));

        String text = renderer.renderBestEffort(resolved, 0).text();

        assertThat(text)
                .contains("Trạng thái: resolved")
                .contains("kết thúc lúc 08:59 UTC ngày 17/11/2026")
                .doesNotContain("TTL")
                .doesNotContain("tự động");
    }

    @Test
    void honestTemplatesCoverEveryNoGeminiBranch() {
        assertThat(renderer.renderHonest(ChatContextOutcome.OUT_OF_SCOPE, null))
                .contains("lượng mưa")
                .contains("gió")
                .contains("bão");
        assertThat(renderer.renderHonest(ChatContextOutcome.NO_DATA, NoDataReason.UNKNOWN_STATION))
                .isEqualTo(
                        "Hệ thống chưa ghi nhận trạm bạn hỏi. Bạn có thể hỏi về lượng mưa, gió hoặc"
                                + " bão đang hoạt động.");
        assertThat(renderer.renderHonest(ChatContextOutcome.NO_DATA, NoDataReason.MISSING_RECORDS))
                .contains("chưa ghi nhận số liệu");
        assertThat(renderer.renderHonest(ChatContextOutcome.NO_DATA, NoDataReason.EMPTY_BBOX))
                .contains("vùng");
        assertThat(renderer.renderHonest(ChatContextOutcome.NO_DATA, NoDataReason.NO_ACTIVE_STORM))
                .contains("không có bão đang hoạt động");
        assertThat(renderer.renderHonest(ChatContextOutcome.NO_SNAPSHOT, null))
                .contains("không còn dữ kiện gốc để giải thích");
    }

    @Test
    void capHoldsWithSeventyFiveViolationsFiveStationsAndFiveStorms() {
        // alert cut to top-5 with remainder 70 (the §7 ladder output)
        FactualContext alert = alertFixture(75, 5, "CRITICAL");
        assertThat(renderer.renderBestEffort(alert, 70).text().length()).isLessThanOrEqualTo(4000);

        // uncut 75-violation snapshot with heavy values escalates to the minimal template
        FactualContext big =
                alertFixture(75, 75, "CRITICAL", 12345.6789, 1234.5678, "wind_gust", "m/s");
        var rendered = renderer.renderBestEffort(big, 0);
        assertThat(rendered.text().length()).isLessThanOrEqualTo(4000);
        assertThat(rendered.minimal()).isTrue();
        assertThat(rendered.text()).contains("tổng 75 vi phạm");

        List<FactualContext.StationObservation> stations = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            stations.add(
                    station(
                            "S" + i,
                            "Trạm rất dài tên " + i + " " + "x".repeat(120),
                            82.4,
                            25.0,
                            NOW.minusSeconds(600),
                            true));
        }
        FactualContext manyStations = context(ChatIntent.RAIN_TOP, stations, List.of(), null);
        assertThat(renderer.renderBestEffort(manyStations, 0).text().length())
                .isLessThanOrEqualTo(4000);

        List<FactualContext.StormSummary> storms = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            storms.add(storm("W504TC" + i, true));
        }
        FactualContext manyStorms = context(ChatIntent.STORM_ACTIVE, List.of(), storms, null);
        assertThat(renderer.renderBestEffort(manyStorms, 0).text().length())
                .isLessThanOrEqualTo(4000);
    }

    @Test
    void compactIsShorterThanFullAndDeterministic() {
        FactualContext ctx = rainFixture();
        String full =
                renderer.renderVerified(
                        ctx,
                        new ChatReplyGuardrail.Selection(
                                ChatReplyGuardrail.Variant.FULL,
                                List.of("DNG", "SGN"),
                                List.of(),
                                List.of()),
                        0);
        String compact =
                renderer.renderVerified(
                        ctx,
                        new ChatReplyGuardrail.Selection(
                                ChatReplyGuardrail.Variant.COMPACT,
                                List.of("DNG", "SGN"),
                                List.of(),
                                List.of()),
                        0);

        assertThat(compact.length()).isLessThan(full.length());
        assertThat(renderer.renderBestEffort(ctx, 0).text())
                .isEqualTo(renderer.renderBestEffort(ctx, 0).text());
        assertThat(full).contains("82.4 mm").contains("12 mm");
    }

    @Test
    void verifiedSubsetKeepsContextRankOrderAndDropsUnearnedSuperlative() {
        var selection =
                new ChatReplyGuardrail.Selection(
                        ChatReplyGuardrail.Variant.FULL,
                        List.of("SGN", "DNG"),
                        List.of(),
                        List.of());

        String text = renderer.renderVerified(rainFixture(), selection, 0);

        assertThat(text.indexOf("DNG")).isLessThan(text.indexOf("SGN"));
        assertThat(text).doesNotContain("cao nhất");
        assertThat(text).contains("82.4 mm").contains("12 mm");
    }

    @Test
    void verifiedAlertRemainderIsRecomputedFromTotalMinusRendered() {
        FactualContext ctx = alertFixture(75, 5, "DANGER");
        var selection =
                new ChatReplyGuardrail.Selection(
                        ChatReplyGuardrail.Variant.FULL, List.of(), List.of(), List.of("K0"));

        String text = renderer.renderVerified(ctx, selection, 74);

        assertThat(text).contains("Và 74 vi phạm khác (không liệt kê chi tiết).");
        assertThat(text).doesNotContain("K1").doesNotContain("K2");
    }

    @Test
    void brokenInvariantsThrowInsteadOfRendering() {
        FactualContext empty = context(ChatIntent.RAIN_TOP, List.of(), List.of(), null);
        assertThatThrownBy(() -> renderer.renderBestEffort(empty, 0))
                .isInstanceOf(IllegalStateException.class);

        FactualContext nonFinite =
                context(
                        ChatIntent.RAIN_TOP,
                        List.of(station("DNG", "Đà Nẵng", Double.NaN, 5.0, NOW, false)),
                        List.of(),
                        null);
        assertThatThrownBy(() -> renderer.renderBestEffort(nonFinite, 0))
                .isInstanceOf(IllegalStateException.class);

        FactualContext missingAlert = context(ChatIntent.ALERT_EXPLAIN, List.of(), List.of(), null);
        assertThatThrownBy(() -> renderer.renderBestEffort(missingAlert, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void numbersAndTimesStayLocaleIndependent() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            String text = renderer.renderBestEffort(rainFixture(), 0).text();
            assertThat(text).contains("82.4").doesNotContain("82,4");
            assertThat(text).contains("08:50 UTC ngày 17/11/2026");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void replyIsPlainTextWithoutMarkdown() {
        String text = renderer.renderBestEffort(rainFixture(), 0).text();
        assertThat(text).doesNotContain("**").doesNotContain("```").doesNotContain("<");
    }
}
