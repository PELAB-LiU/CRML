package org.openmodelica.crml.testutil.vql;

import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Which file each model object is stored in, as a model that queries can navigate.
 * A resource is not an EObject, so VQL cannot reach it; this index stands in for it:
 * one {@code File} object per resource, with the resource's file name and its root
 * objects.
 *
 * <pre>
 * package files (nsURI {@value #NS_URI})
 *   class File { String name; refers EObject[] roots }
 * </pre>
 *
 * The index lives in a resource set of its own, which the query engine is given
 * next to the models, so the models are left untouched.
 */
final class FileIndex {

    static final String NS_URI = "http://openmodelica.org/crml/testutil/files";

    static final EPackage PACKAGE;
    private static final EClass FILE;
    private static final EAttribute NAME;
    private static final EReference ROOTS;

    static {
        EcoreFactory f = EcoreFactory.eINSTANCE;
        PACKAGE = f.createEPackage();
        PACKAGE.setName("files");
        PACKAGE.setNsPrefix("files");
        PACKAGE.setNsURI(NS_URI);
        FILE = f.createEClass();
        FILE.setName("File");
        PACKAGE.getEClassifiers().add(FILE);
        NAME = f.createEAttribute();
        NAME.setName("name");
        NAME.setEType(EcorePackage.Literals.ESTRING);
        FILE.getEStructuralFeatures().add(NAME);
        ROOTS = f.createEReference();
        ROOTS.setName("roots");
        ROOTS.setEType(EcorePackage.Literals.EOBJECT);
        ROOTS.setUpperBound(-1);
        FILE.getEStructuralFeatures().add(ROOTS);
        // The pattern parser resolves imports by nsURI, and needs the package in a
        // resource to refer to its classes.
        new ResourceImpl(URI.createURI(NS_URI)).getContents().add(PACKAGE);
        EPackage.Registry.INSTANCE.put(NS_URI, PACKAGE);
    }

    private FileIndex() {
    }

    /** A new resource set holding one {@code File} per resource of {@code models}. */
    static ResourceSet of(ResourceSet models) {
        ResourceSet index = new ResourceSetImpl();
        Resource resource = new ResourceImpl(URI.createURI("files:/index"));
        index.getResources().add(resource);
        for (Resource model : models.getResources()) {
            EObject file = EcoreUtil.create(FILE);
            file.eSet(NAME, fileName(model));
            @SuppressWarnings("unchecked")
            List<EObject> roots = (List<EObject>) file.eGet(ROOTS);
            roots.addAll(model.getContents());
            resource.getContents().add(file);
        }
        return index;
    }

    /** The last segment of the resource URI, decoded; "" if it has none. */
    static String fileName(Resource resource) {
        URI uri = resource.getURI();
        String last = uri == null ? null : uri.lastSegment();
        return last == null ? "" : URI.decode(last);
    }
}
