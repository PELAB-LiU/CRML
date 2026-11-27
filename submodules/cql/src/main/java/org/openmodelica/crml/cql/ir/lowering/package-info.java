/**
 * Rewritings of the IR shared by the backends that generate queries: negation normal form,
 * violation queries (spec section 9.2), {@code let} inlining.
 *
 * <p>Not used by the reference interpreter (QUERY_LANGUAGE_ARCHITECTURE.md, decision A5).
 */
package org.openmodelica.crml.cql.ir.lowering;
