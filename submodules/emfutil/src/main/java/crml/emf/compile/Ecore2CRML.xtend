package crml.emf.compile

import org.eclipse.emf.ecore.EClass

class CRMLTemplate {
    def dispatch translate(EClass clazz){

        return '''
        class «clazz.getName()» is {
            String placeholder;
        }
        '''
    }
    def dispatch translate(EReference ref){

        return '''
        class «clazz.getName()» is {
            String placeholder;
        }
        '''
    }
}