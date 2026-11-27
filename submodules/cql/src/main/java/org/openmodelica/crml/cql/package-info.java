/**
 * The CQL frontend: the model query language of QUERY_LANGUAGE_SPEC.md, up to the backend boundary.
 *
 * <p>Tests use {@link org.openmodelica.crml.cql.api}; backends implement {@link org.openmodelica.crml.cql.spi}
 * and read the IR ({@link org.openmodelica.crml.cql.ir}), the metamodels ({@link org.openmodelica.crml.cql.schema})
 * and the models ({@link org.openmodelica.crml.cql.scope}). See QUERY_LANGUAGE_ARCHITECTURE.md, section 4.2.
 */
package org.openmodelica.crml.cql;
