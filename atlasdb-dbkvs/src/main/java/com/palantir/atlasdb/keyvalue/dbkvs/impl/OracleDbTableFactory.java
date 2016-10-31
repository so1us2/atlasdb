/**
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.keyvalue.dbkvs.impl;

import com.palantir.atlasdb.keyvalue.api.TableReference;
import com.palantir.atlasdb.keyvalue.dbkvs.OracleDdlConfig;
import com.palantir.atlasdb.keyvalue.dbkvs.OracleTableNameMapper;
import com.palantir.atlasdb.keyvalue.dbkvs.impl.oracle.OracleDdlTable;
import com.palantir.atlasdb.keyvalue.dbkvs.impl.oracle.OracleOverflowQueryFactory;
import com.palantir.atlasdb.keyvalue.dbkvs.impl.oracle.OracleOverflowWriteTable;
import com.palantir.atlasdb.keyvalue.dbkvs.impl.oracle.OracleRawQueryFactory;
import com.palantir.atlasdb.keyvalue.dbkvs.impl.oracle.OracleTableInitializer;
import com.palantir.nexus.db.DBType;

public class OracleDbTableFactory implements DbTableFactory {
    private final OracleDdlConfig config;
    private final OracleTableNameMapper oracleTableNameMapper;

    private OracleDbTableFactory(OracleDdlConfig config, OracleTableNameMapper oracleTableNameMapper) {
        this.config = config;
        this.oracleTableNameMapper = oracleTableNameMapper;
    }

    public static OracleDbTableFactory create(OracleDdlConfig config) {
        return new OracleDbTableFactory(config, new OracleTableNameMapper());
    }

    @Override
    public DbMetadataTable createMetadata(TableReference tableRef, ConnectionSupplier conns) {
        return new SimpleDbMetadataTable(tableRef, conns, config);
    }

    @Override
    public DbDdlTable createDdl(TableReference tableRef, ConnectionSupplier conns) {
        return new OracleDdlTable(tableRef, conns, config, oracleTableNameMapper);
    }

    @Override
    public DbTableInitializer createInitializer(ConnectionSupplier conns) {
        return new OracleTableInitializer(conns, config);
    }

    @Override
    public DbReadTable createRead(TableReference tableRef, ConnectionSupplier conns) {
        TableSize tableSize = TableSizeCache.getTableSize(conns, tableRef, config.metadataTable());
        DbQueryFactory queryFactory;
        String shortTableName = getShortTableName(tableRef);
        switch (tableSize) {
            case OVERFLOW:
                String shortOverflowTableName = getShortOverflowTableName(tableRef);
                queryFactory = new OracleOverflowQueryFactory(config, shortTableName, shortOverflowTableName);
                break;
            case RAW:
                queryFactory = new OracleRawQueryFactory(shortTableName, config);
                break;
            default:
                throw new EnumConstantNotPresentException(TableSize.class, tableSize.name());
        }
        return new UnbatchedDbReadTable(conns, queryFactory);
    }

    @Override
    public DbWriteTable createWrite(TableReference tableRef, ConnectionSupplier conns) {
        TableSize tableSize = TableSizeCache.getTableSize(conns, tableRef, config.metadataTable());
        switch (tableSize) {
            case OVERFLOW:
                return OracleOverflowWriteTable.create(config, tableRef, conns, oracleTableNameMapper);
            case RAW:
                return new SimpleDbWriteTable(tableRef, conns, config);
            default:
                throw new EnumConstantNotPresentException(TableSize.class, tableSize.name());
        }
    }

    private String getShortTableName(TableReference tableRef) {
        return oracleTableNameMapper.getShortPrefixedTableName(config.tablePrefix(), tableRef);
    }

    private String getShortOverflowTableName(TableReference tableRef) {
        return oracleTableNameMapper.getShortPrefixedTableName(config.overflowTablePrefix(), tableRef);
    }

    @Override
    public DBType getDbType() {
        return DBType.ORACLE;
    }

    @Override
    public void close() {
        // do nothing
    }
}
