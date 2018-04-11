package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.Queuer;
import org.folio.rest.jaxrs.model.QueuerCollection;
import org.folio.rest.jaxrs.resource.QueuersResource;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.utils.PgQuery;
import org.folio.rest.utils.PgTransaction;

public class QueuerAPI implements QueuersResource {

	private static final Logger logger = LoggerFactory.getLogger(QueuerAPI.class);

	private final PostgresClient pgClient;
	private static final String[] ALL_FIELDS = { "*" };
	private static final String QUEUERS_TABLE = "queuers";

	public QueuerAPI(Vertx vertx, String tenantId) {
		this.pgClient = PostgresClient.getInstance(vertx, tenantId);
		this.pgClient.setIdField("id");
	}

	@Override
	public void getQueuers(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
		logger.info("GET queuers...");
		PgQuery.PgQueryBuilder pgQueryBuilder = new PgQuery.PgQueryBuilder(ALL_FIELDS, QUEUERS_TABLE).query(query).offset(offset).limit(limit);
		Future.succeededFuture(pgQueryBuilder)
			.compose(this::runGetQuery)
			.compose(this::parseGetResults)
			.setHandler(asyncResultHandler);
	}

	@Override
	public void postQueuers(String lang, Queuer entity, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
		logger.info("POST queuers...");
		PgTransaction<Queuer> pgTransaction = new PgTransaction<Queuer>(entity);
		Future.succeededFuture(pgTransaction)
			.compose(this::startTx)
			.compose(this::saveQueuers)
			.compose(this::endTx)
			.compose(this::parsePostResults)
			.setHandler(asyncResultHandler);
	}

	private Future<Object[]> runGetQuery(PgQuery.PgQueryBuilder queryBuilder) {
		Future<Object[]> future = Future.future();
		try {
			PgQuery query = queryBuilder.build();
			pgClient.get(query.getTable(), Queuer.class, query.getFields(), query.getCql(), true, false,
					future.completer());
		} catch (Exception e) {
			future.fail(new Throwable(e));
		}
		return future;
	}

	@SuppressWarnings("unchecked")
	public Future<Response> parseGetResults(Object[] resultSet) {
		List<Queuer> queuers = (List<Queuer>) resultSet[0];
		int totalRecords = (Integer) resultSet[1];
		QueuerCollection queuerCollection = new QueuerCollection();
		queuerCollection.setQueuers(queuers);
		queuerCollection.setTotalRecords(totalRecords);
		Response response = GetQueuersResponse.withJsonOK(queuerCollection);
		return Future.succeededFuture(response);
	}

	private Future<PgTransaction<Queuer>> startTx(PgTransaction<Queuer> tx) {
		Future<PgTransaction<Queuer>> future = Future.future();
		pgClient.startTx(sqlConnection -> {
			tx.sqlConnection = sqlConnection;
			future.complete(tx);
		});
		return future;
	}

	private Future<PgTransaction<Queuer>> saveQueuers(PgTransaction<Queuer> tx) {
		Future<PgTransaction<Queuer>> future = Future.future();
		try {
			tx.entity.setEmailedDate(null);
			tx.entity.setCreateDate(Date.from(Instant.now()));
			pgClient.save(tx.sqlConnection, QUEUERS_TABLE, tx.entity, location -> {
				tx.location = location;
				future.complete(tx);
			});
		} catch (Exception e) {
			future.fail(new Throwable(e));
		}
		return future;
	}

	private Future<PgTransaction<Queuer>> endTx(PgTransaction<Queuer> tx) {
		Future<PgTransaction<Queuer>> future = Future.future();
		pgClient.endTx(tx.sqlConnection, v -> {
			future.complete(tx);
		});
		return future;
	}

	private Future<Response> parsePostResults(PgTransaction<Queuer> tx) {
		final String location = tx.location.result();
		final Queuer queuer = tx.entity;
		queuer.setId(tx.entity.getId());
		OutStream entity = new OutStream();
		entity.setData(queuer);
		Response response = PostQueuersResponse.withJsonCreated(location, entity);
		return Future.succeededFuture(response);
	}
}
