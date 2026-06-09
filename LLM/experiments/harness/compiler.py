import jpype


class CRMLCompileError(Exception):
    pass


class CRMLCompiler:
    """Compiles CRML source strings to Modelica via JPype.

    The JVM must already be started (e.g. via GradleJvm) before constructing this.
    """

    def __init__(self) -> None:
        self._Parser = jpype.JClass("crml.language.util.Parser")
        self._OMGenerator = jpype.JClass("crml.compiler.omc.OMGenerator")

    def compile(self, crml_source: str, pkg_name: str) -> tuple[str, str]:
        """Compile CRML source to Modelica.

        Returns (modelica_code, filename) where modelica_code starts with
        `within <pkg_name>;` and filename is e.g. `SRI.mo`.
        """
        result = self._Parser().parse(crml_source)
        if result.syntax().hasErrors():
            errors = [str(e) for e in result.syntax().errors()]
            raise CRMLCompileError("CRML syntax errors:\n" + "\n".join(errors))

        generator = self._OMGenerator(result, False)
        mo_code = str(generator.getModelicaCode(pkg_name))
        filename = str(generator.filename())
        return mo_code, filename
