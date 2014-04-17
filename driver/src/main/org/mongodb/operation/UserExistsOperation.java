/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mongodb.operation;

import org.mongodb.CommandResult;
import org.mongodb.Document;
import org.mongodb.Function;
import org.mongodb.MongoFuture;
import org.mongodb.MongoNamespace;
import org.mongodb.codecs.DocumentCodec;
import org.mongodb.connection.ServerVersion;
import org.mongodb.protocol.QueryProtocol;
import org.mongodb.protocol.QueryResult;
import org.mongodb.session.ServerConnectionProvider;
import org.mongodb.session.Session;

import java.util.EnumSet;
import java.util.List;

import static org.mongodb.assertions.Assertions.notNull;
import static org.mongodb.operation.OperationHelper.executeProtocol;
import static org.mongodb.operation.OperationHelper.executeProtocolAsync;
import static org.mongodb.operation.OperationHelper.executeWrappedCommandProtocol;
import static org.mongodb.operation.OperationHelper.executeWrappedCommandProtocolAsync;
import static org.mongodb.operation.OperationHelper.getPrimaryConnectionProvider;
import static org.mongodb.operation.OperationHelper.serverVersionIsAtLeast;
import static org.mongodb.operation.OperationHelper.transformResult;

/**
 * An operation to determine if a user exists.
 *
 * @since 3.0
 */
public class UserExistsOperation implements AsyncOperation<Boolean>, Operation<Boolean> {

    private final String database;
    private final String userName;

    public UserExistsOperation(final String source, final String userName) {
        this.database = notNull("source", source);
        this.userName = notNull("userName", userName);
    }
    @Override
    public Boolean execute(final Session session) {
        ServerConnectionProvider connectionProvider = getPrimaryConnectionProvider(session);
        if (serverVersionIsAtLeast(connectionProvider, new ServerVersion(2, 6))) {
            CommandResult result = executeWrappedCommandProtocol(database, getCommand(), connectionProvider);
            return transformResult(result, transformCommandResult());
        } else {
            QueryResult<Document> result = executeProtocol(getCollectionBasedProtocol(), connectionProvider);
            return transformResult(result, transformQueryResult());
        }
    }

    @Override
    public MongoFuture<Boolean> executeAsync(final Session session) {
        ServerConnectionProvider connectionProvider = getPrimaryConnectionProvider(session);
        if (serverVersionIsAtLeast(connectionProvider, new ServerVersion(2, 6))) {
            MongoFuture<CommandResult> result = executeWrappedCommandProtocolAsync(database, getCommand(), connectionProvider);
            return transformResult(result, transformCommandResult());
        } else {
            MongoFuture<QueryResult<Document>> result = executeProtocolAsync(getCollectionBasedProtocol(), session);
            return transformResult(result, transformQueryResult());
        }
    }

    private Function<CommandResult, Boolean> transformCommandResult() {
        return new Function<CommandResult, Boolean>() {
            @Override
            public Boolean apply(final CommandResult commandResult) {
                return !commandResult.getResponse().get("users", List.class).isEmpty();
            }
        };
    }

    private Function<QueryResult<Document>, Boolean> transformQueryResult() {
        return new Function<QueryResult<Document>, Boolean>() {
            @Override
            public Boolean apply(final QueryResult<Document> queryResult) {
                return !queryResult.getResults().isEmpty();
            }
        };
    }

    private QueryProtocol<Document> getCollectionBasedProtocol() {
        MongoNamespace namespace = new MongoNamespace(database, "system.users");
        DocumentCodec codec = new DocumentCodec();
        return new QueryProtocol<Document>(namespace, EnumSet.noneOf(QueryFlag.class), 0, 1,
                new Document("user", userName), null, codec, codec);
    }

    private Document getCommand() {
        return new Document("usersInfo", userName);
    }

}