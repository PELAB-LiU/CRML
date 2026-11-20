package org.openmodelica.crml.testutil.vql;

import static j2html.TagCreator.code;
import static j2html.TagCreator.details;
import static j2html.TagCreator.pre;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.td;
import static j2html.TagCreator.th;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.tr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openmodelica.crml.testutil.CustomHtmlReporter;

import j2html.tags.DomContent;

/**
 * The results of a VQL expectation file as one table, one row per constraint:
 * line, pattern (its source folded under the name), severity, status and the
 * violations. In the style of the verification report's outputs table.
 */
public final class ExpectationTable implements CustomHtmlReporter {

    private final List<ExpectationResult> results;

    private ExpectationTable(List<ExpectationResult> results) {
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
    }

    public static ExpectationTable of(List<ExpectationResult> results) {
        return new ExpectationTable(results);
    }

    @Override
    public Object report() {
        List<DomContent> rows = new ArrayList<>();
        for (ExpectationResult result : results) {
            DomContent pattern = result.source.isEmpty()
                ? code(result.pattern)
                : details(j2html.TagCreator.summary(code(result.pattern)), pre(result.source));
            rows.add(tr(
                td(String.valueOf(result.line)),
                td(pattern),
                td(result.severity),
                td(result.status.toString()),
                td(result.detail)));
        }
        return table(
            thead(tr(th("#"), th("Constraint"), th("Severity"), th("Status"), th("Detail"))),
            tbody(rows.toArray(new DomContent[0])));
    }
}
