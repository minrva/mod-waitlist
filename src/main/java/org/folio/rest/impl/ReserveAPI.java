package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemCollection;
import org.folio.rest.jaxrs.resource.ReservesResource;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.utils.PgQuery;

public class ReserveAPI implements ReservesResource {

	private static final Logger logger = LoggerFactory.getLogger(ReserveAPI.class);

	private final PostgresClient pgClient;
	private static final String[] ALL_FIELDS = { "*" };
	private static final String ITEMS_TABLE = "items";

	public ReserveAPI(Vertx vertx, String tenantId) {
		this.pgClient = PostgresClient.getInstance(vertx, tenantId);
		this.pgClient.setIdField("id");
	}

	@Override
	public void getReserves(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
		logger.info("GET reserves...");
		PgQuery.PgQueryBuilder queryBuilder = new PgQuery.PgQueryBuilder(ALL_FIELDS, ITEMS_TABLE).query(query).offset(offset).limit(limit);
		Future.succeededFuture(queryBuilder)
			.compose(this::runGetQuery)
			.compose(this::parseGetResults)
			.setHandler(asyncResultHandler);
	}

	@Override
	public void postReserves(String lang, Item entity, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
		logger.info("POST reserves not implemented...");
		throw new Exception("postReserves not implemented.");
	}

	private Future<Object[]> runGetQuery(PgQuery.PgQueryBuilder queryBuilder) {
		Future<Object[]> future = Future.future();
		try {
			PgQuery query = queryBuilder.build();
			pgClient.get(query.getTable(), Item.class, query.getFields(), query.getCql(), true, false,
					future.completer());
		} catch (Exception e) {
			future.fail(new Throwable(e));
		}
		return future;
	}

	@SuppressWarnings("unchecked")
	public Future<Response> parseGetResults(Object[] resultSet) {
		List<Item> items = (List<Item>) resultSet[0];
		int totalRecords = (Integer) resultSet[1];
		ItemCollection itemCollection = new ItemCollection();
		itemCollection.setItems(items);
		itemCollection.setTotalRecords(totalRecords);
		Response response = GetReservesResponse.withJsonOK(itemCollection);
		return Future.succeededFuture(response);
	}
}
