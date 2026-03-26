package crml.emf.compile;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;

import java.util.List;
import java.io.File;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

public class Compiler {
    public final ResourceSet resourceSet = new ResourceSetImpl();

    public String remap(){
        File metamodel = new File("foo");
        Resource meta = resourceSet.getResource(URI.createFileURI(metamodel.getAbsolutePath()), true);
        EPackage pkg = getMetamodelsOfResource(meta).getFirst();//forEach(it -> EPackage.Registry.INSTANCE.putIfAbsent(it.getNsURI(), it));

        CRMLTemplate template = new CRMLTemplate();
        return template.translate((EClass)pkg.getEClassifiers().getFirst());

    }

    public List<EPackage> getMetamodelsOfResource(Resource resource){
        return resource.getContents().stream()
            .filter(it -> it instanceof EPackage)
            .map(it -> (EPackage) it)
            .toList();
    }
}
