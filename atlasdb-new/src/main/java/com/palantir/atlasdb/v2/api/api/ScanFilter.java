/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.atlasdb.v2.api.api;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

import org.immutables.value.Value;

import com.google.common.collect.ImmutableSet;
import com.palantir.atlasdb.v2.api.api.NewIds.Cell;
import com.palantir.atlasdb.v2.api.api.NewIds.Column;
import com.palantir.atlasdb.v2.api.api.NewIds.Row;

public abstract class ScanFilter {
    private ScanFilter() {}

    public abstract <T> T accept(Visitor<T> visitor);

    // TODO make private somewhere
    public Comparator<Cell> toComparator(ScanAttributes attributes) {
        return accept(new ComparatorVisitor(attributes));
    }

    // somethinig about is this the right place to pass in the limit? It means we probably have to
    // splice together in-memory and out-of-memory writes separately.
    public static ScanFilter rowsAndColumns(RowsFilter rows, ColumnsFilter columns, int limit) {
        return ImmutableRowsAndColumnsFilter.of(rows, columns, limit);
    }

    public static ScanFilter cells(Iterable<? extends Cell> cells) {
        return ImmutableCellsFilter.of(cells);
    }

    public static ScanFilter withStoppingPoint(ScanFilter inner, Cell lastCellInclusive) {
        return ImmutableWithStoppingPoint.of(inner, lastCellInclusive);
    }

    @Value.Check
    final void noNestingAllowed() {
        accept(new Visitor<Void>() {
            @Override
            public Void rowsAndColumns(RowsFilter rows, ColumnsFilter columns, int limit) {
                return null;
            }

            @Override
            public Void cells(Set<Cell> cells) {
                return null;
            }

            @Override
            public Void withStoppingPoint(ScanFilter inner, Cell lastCellInclusive) {
                return inner.accept(new Visitor<Void>() {
                    @Override
                    public Void rowsAndColumns(RowsFilter rows, ColumnsFilter columns, int limit) {
                        return null;
                    }

                    @Override
                    public Void cells(Set<Cell> cells) {
                        return null;
                    }

                    @Override
                    public Void withStoppingPoint(ScanFilter inner, Cell lastCellInclusive) {
                        throw new IllegalStateException("Cannot nest stopping points");
                    }
                });
            }
        });
    }

    public interface Visitor<T> {
        T rowsAndColumns(RowsFilter rows, ColumnsFilter columns, int limit);
        T cells(Set<Cell> cells);
        T withStoppingPoint(ScanFilter inner, Cell lastCellInclusive);
    }

    @Value.Immutable
    static abstract class RowsAndColumnsFilter extends ScanFilter {
        @Value.Parameter
        abstract RowsFilter rows();

        @Value.Parameter
        abstract ColumnsFilter columns();

        @Value.Parameter
        abstract int limit();

        @Override
        public final <T> T accept(Visitor<T> visitor) {
            return visitor.rowsAndColumns(rows(), columns(), limit());
        }
    }

    @Value.Immutable
    static abstract class CellsFilter extends ScanFilter {
        @Value.Parameter
        abstract Set<Cell> cells();

        @Override
        public final <T> T accept(Visitor<T> visitor) {
            return visitor.cells(cells());
        }
    }

    @Value.Immutable
    static abstract class WithStoppingPoint extends ScanFilter {
        @Value.Parameter
        abstract ScanFilter inner();

        @Value.Parameter
        abstract Cell lastCellInclusive();

        @Override
        public final <T> T accept(Visitor<T> visitor) {
            return visitor.withStoppingPoint(inner(), lastCellInclusive());
        }
    }

    public static ScanFilter forCell(Cell cell) {
        return cells(ImmutableSet.of(cell));
    }

    public static RowsFilter allRows() {
        return AllRowsFilter.INSTANCE;
    }

    public static RowsFilter exactRows(Set<Row> rows) {
        return ImmutableExactRows.of(rows);
    }

    public static RowsFilter rowRange(Optional<Row> from, Optional<Row> to) {
        return ImmutableRowRange.of(from, to);
    }

    public static ColumnsFilter allColumns() {
        return AllColumnsFilter.INSTANCE;
    }

    public static ColumnsFilter exactColumns(Set<Column> columns) {
        return ImmutableExactColumns.of(columns);
    }

    public static ColumnsFilter columnRange(Optional<Column> from, Optional<Column> to) {
        return ImmutableColumnRange.of(from, to);
    }

    public interface RowsFilter {
        <T> T accept(Visitor<T> visitor);

        interface Visitor<T> {
            T visitAllRows();
            T visitExactRows(Set<Row> rows);
            T visitRowRange(Optional<Row> fromInclusive, Optional<Row> toExclusive);
        }
    }

    public interface ColumnsFilter {
        <T> T accept(Visitor<T> visitor);

        interface Visitor<T> {
            T visitAllColumns();
            T visitExactColumns(Set<Column> columns);
            T visitColumnRange(Optional<Column> fromInclusive, Optional<Column> toExclusive);
        }
    }

    private enum AllRowsFilter implements RowsFilter {
        INSTANCE;

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitAllRows();
        }
    }

    private enum AllColumnsFilter implements ColumnsFilter {
        INSTANCE;

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitAllColumns();
        }
    }

    @Value.Immutable
    interface ExactRows extends RowsFilter {
        @Value.Parameter
        Set<Row> rows();

        @Override
        default <T> T accept(Visitor<T> visitor) {
            return visitor.visitExactRows(rows());
        }
    }

    @Value.Immutable
    interface ExactColumns extends ColumnsFilter {
        @Value.Parameter
        Set<Column> columns();

        @Override
        default <T> T accept(Visitor<T> visitor) {
            return visitor.visitExactColumns(columns());
        }
    }

    @Value.Immutable
    interface RowRange extends RowsFilter {
        @Value.Parameter
        Optional<Row> fromInclusive();

        @Value.Parameter
        Optional<Row> toExclusive();

        default <T> T accept(Visitor<T> visitor) {
            return visitor.visitRowRange(fromInclusive(), toExclusive());
        }
    }

    @Value.Immutable
    interface ColumnRange extends ColumnsFilter {
        @Value.Parameter
        Optional<Column> fromInclusive();

        @Value.Parameter
        Optional<Column> toExclusive();

        default <T> T accept(Visitor<T> visitor) {
            return visitor.visitColumnRange(fromInclusive(), toExclusive());
        }
    }

    // todo make this production ready
    private static final class ComparatorVisitor implements Visitor<Comparator<Cell>> {
        private final ScanAttributes attributes;

        private ComparatorVisitor(ScanAttributes attributes) {
            this.attributes = attributes;
        }

        @Override
        public Comparator<Cell> rowsAndColumns(RowsFilter rows, ColumnsFilter columns, int limit) {
            return Comparator.naturalOrder();
        }

        @Override
        public Comparator<Cell> cells(Set<Cell> cells) {
            return Comparator.naturalOrder();
        }

        @Override
        public Comparator<Cell> withStoppingPoint(ScanFilter inner, Cell lastCellInclusive) {
            return inner.accept(this);
        }
    }
}
