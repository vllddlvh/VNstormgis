package vn.weathergis.chat.context;

/** Outcome plus the immutable factual context handed to W5-03/W5-04. */
public record ChatContextResult(
        ChatContextOutcome outcome, NoDataReason noDataReason, FactualContext context) {}
