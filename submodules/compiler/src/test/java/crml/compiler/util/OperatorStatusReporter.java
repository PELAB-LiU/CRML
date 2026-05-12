package crml.compiler.util;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import crml.compiler.translation.LibraryDefinition;
import crml.test.CustomHtmlReporter;

import static j2html.TagCreator.each;
import static j2html.TagCreator.join;
import static j2html.TagCreator.p;
import static j2html.TagCreator.table;
import static j2html.TagCreator.td;
import static j2html.TagCreator.th;
import static j2html.TagCreator.tr;

public class OperatorStatusReporter implements CustomHtmlReporter {

    private final String title;
    private final Set<String> presentAndExpected;
    private final Set<String> presentNotExpected;
    private final Set<String> expectedNotPresent;

    private OperatorStatusReporter(String title, Collection<String> present, List<String> expected) {
        this.title = title;
        Set<String> expectedSet = new LinkedHashSet<>(expected);
        Set<String> presentSet = new LinkedHashSet<>(present);

        presentAndExpected = presentSet.stream()
                .filter(expectedSet::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        presentNotExpected = presentSet.stream()
                .filter(k -> !expectedSet.contains(k))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        expectedNotPresent = expectedSet.stream()
                .filter(k -> !presentSet.contains(k))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static OperatorStatusReporter ofOperators(LibraryDefinition lib, List<String> expected) {
        return new OperatorStatusReporter("Operators", lib.operators().keySet(), expected);
    }

    public static OperatorStatusReporter ofCategories(LibraryDefinition lib, List<String> expected) {
        return new OperatorStatusReporter("Categories", lib.categories().names(), expected);
    }

    @Override
    public Object report() {
        return join(
            p(title + " status"),
            table(
                tr(
                    th("Status"),
                    th("Name")
                ),
                each(presentAndExpected, name ->
                    tr(
                        td("Present & expected").withStyle("color:green"),
                        td(name)
                    )
                ),
                each(expectedNotPresent, name ->
                    tr(
                        td("Expected but missing").withStyle("color:red"),
                        td(name)
                    )
                ),
                each(presentNotExpected, name ->
                    tr(
                        td("Present, not expected").withStyle("color:orange"),
                        td(name)
                    )
                )
            )
        );
    }
}
