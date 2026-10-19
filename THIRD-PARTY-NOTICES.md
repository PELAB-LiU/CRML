# Third-party notices

> **Draft.** This file currently lists only the dependencies licensed under the
> Eclipse Public License. Notices for the remaining (Apache-2.0, BSD, MIT)
> dependencies are still to be added.

CRML is distributed under the OSMC Public License (OSMC-PL) version 1.8; see
`OSMC-License.txt`. When used under its GNU AGPL version 3 mode, an additional
permission under section 7 of the AGPL covers linking with the libraries below.

## Eclipse Public License 2.0 (EPL-2.0)

These libraries are redistributed unmodified, in object code form, as part of
the CRML runtime (`compiler-all.jar`) and the CRML language server. Their source
code is available from the repositories listed. The license text is available
at <https://www.eclipse.org/legal/epl-2.0/>.

### CRML compiler and language runtime

| Library | Maven coordinates | Version | Source |
|---|---|---|---|
| EMF Common | `org.eclipse.emf:org.eclipse.emf.common` | 2.28.0 | <https://github.com/eclipse-emf/org.eclipse.emf> |
| EMF Ecore | `org.eclipse.emf:org.eclipse.emf.ecore` | 2.33.0 | <https://github.com/eclipse-emf/org.eclipse.emf> |
| EMF Xcore runtime library | `org.eclipse.emf:org.eclipse.emf.ecore.xcore.lib` | 1.6.0 | <https://github.com/eclipse-emf/org.eclipse.emf> |
| Xtext Xbase runtime library | `org.eclipse.xtext:org.eclipse.xtext.xbase.lib` | 2.25.0 | <https://github.com/eclipse-xtext/xtext> |

### CRML language server

In addition to the libraries above:

| Library | Maven coordinates | Version | Source |
|---|---|---|---|
| Eclipse LSP4J | `org.eclipse.lsp4j:org.eclipse.lsp4j` | 1.0.0 | <https://github.com/eclipse-lsp4j/lsp4j> |
| Eclipse LSP4J JSON-RPC | `org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc` | 1.0.0 | <https://github.com/eclipse-lsp4j/lsp4j> |

### Build-time only (not redistributed)

EMF Codegen, Xcore and Xtext generate the metamodel code during the build. They
are not part of any CRML distribution artifact, so their notices are not
reproduced here.
