/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.ql.expression;

import org.elasticsearch.xpack.ql.QlIllegalArgumentException;
import org.elasticsearch.xpack.ql.capabilities.Resolvable;
import org.elasticsearch.xpack.ql.capabilities.Resolvables;
import org.elasticsearch.xpack.ql.tree.Node;
import org.elasticsearch.xpack.ql.tree.Source;
import org.elasticsearch.xpack.ql.type.DataType;
import org.elasticsearch.xpack.ql.util.StringUtils;

import java.util.List;

/**
 * In a SQL statement, an Expression is whatever a user specifies inside an
 * action, so for instance:
 *
 * {@code SELECT a, b, ABS(c) FROM i}
 *
 * a, b, ABS(c), and i are all Expressions, with ABS(c) being a Function
 * (which is a type of expression) with a single child, c.
 */
public abstract class Expression extends Node<Expression> implements Resolvable {

    public static class TypeResolution {
        private final boolean failed;
        private final String message;

        public static final TypeResolution TYPE_RESOLVED = new TypeResolution(false, StringUtils.EMPTY);

        public TypeResolution(String message) {
            this(true, message);
        }

        private TypeResolution(boolean unresolved, String message) {
            this.failed = unresolved;
            this.message = message;
        }

        public boolean unresolved() {
            return failed;
        }

        public boolean resolved() {
            return failed == false;
        }

        public TypeResolution and(TypeResolution other) {
            return failed ? this : other;
        }

        public String message() {
            return message;
        }

        @Override
        public String toString() {
            return resolved() ? "" : message;
        }
    }

    private TypeResolution lazyTypeResolution = null;
    private Boolean lazyChildrenResolved = null;
    private Expression lazyCanonical = null;
    private AttributeSet lazyReferences = null;

    public Expression(Source source, List<Expression> children) {
        super(source, children);
    }

    // whether the expression can be evaluated statically (folded) or not
    public boolean foldable() {
        return false;
    }

    public Object fold() {
        throw new QlIllegalArgumentException("Should not fold expression");
    }

    public abstract Nullability nullable();

    // the references/inputs/leaves of the expression tree
    public AttributeSet references() {
        if (lazyReferences == null) {
            lazyReferences = Expressions.references(children());
        }
        return lazyReferences;
    }

    public boolean childrenResolved() {
        if (lazyChildrenResolved == null) {
            lazyChildrenResolved = Boolean.valueOf(Resolvables.resolved(children()));
        }
        return lazyChildrenResolved;
    }

    public final TypeResolution typeResolved() {
        if (lazyTypeResolution == null) {
            lazyTypeResolution = resolveType();
        }
        return lazyTypeResolution;
    }

    protected TypeResolution resolveType() {
        return TypeResolution.TYPE_RESOLVED;
    }

    public final Expression canonical() {
        if (lazyCanonical == null) {
            lazyCanonical = canonicalize();
        }
        return lazyCanonical;
    }

    protected Expression canonicalize() {
        if (children().isEmpty()) {
            return this;
        }
        List<Expression> canonicalChildren = Expressions.canonicalize(children());
        // check if replacement is really needed
        if (children().equals(canonicalChildren)) {
            return this;
        }
        return replaceChildrenSameSize(canonicalChildren);
    }

    public boolean semanticEquals(Expression other) {
        return canonical().equals(other.canonical());
    }

    public int semanticHash() {
        return canonical().hashCode();
    }

    @Override
    public boolean resolved() {
        return childrenResolved() && typeResolved().resolved();
    }

    public abstract DataType dataType();

    @Override
    public String toString() {
        return sourceText();
    }

    @Override
    public String propertiesToString(boolean skipIfChild) {
        return super.propertiesToString(false);
    }
}
