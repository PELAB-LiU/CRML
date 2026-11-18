package org.openmodelica.crml.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.resource.Resource;
import org.junit.jupiter.api.Test;
import org.openmodelica.crml.language.workspace.CrmlDiagnostic;
import org.openmodelica.crml.language.workspace.CrmlResource;
import org.openmodelica.crml.language.pretty.Misc;
import org.openmodelica.crml.model.metamodel.Sequence;

/** The bundled libraries load, link and resolve without problems. */
class LibrariesTest {
    @Test
    void bundledLibrariesLoadCleanly() {
        List<String> problems = new ArrayList<>();
        for (CrmlResource library : new Analyzer().libraries()) {
            for (Resource.Diagnostic problem : library.getErrors()) {
                problems.add(library.getURI().lastSegment() + ": " + CrmlDiagnostic.format(problem, CrmlDiagnostic.LocationStyle.NONE));
            }
            library.getAllContents().forEachRemaining(object -> {
                if (object instanceof Sequence && !(object.eContainer() instanceof Sequence)) {
                    problems.add(library.getURI().lastSegment() + ": unresolved operator call " + text(object));
                }
            });
            if (library.getState() != CrmlResource.State.RESOLVED) {
                problems.add(library.getURI().lastSegment() + ": " + library.getState());
            }
        }
        assertEquals(List.of(), problems);
    }

    private static String text(org.eclipse.emf.ecore.EObject object) {
        try {
            return Misc.pretty((Sequence) object);
        } catch (RuntimeException e) {
            return object.toString();
        }
    }
}
