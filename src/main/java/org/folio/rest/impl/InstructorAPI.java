package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Instructor;
import org.folio.rest.jaxrs.model.InstructorCollection;
import org.folio.rest.jaxrs.resource.InstructorsResource;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.utils.PgQuery;
import java.util.List;

public class InstructorAPI implements InstructorsResource {

	private static final Logger logger = LoggerFactory.getLogger(InstructorAPI.class);

	private final PostgresClient pgClient;
	private static final String[] ALL_FIELDS = { "*" };
	private static final String INSTRUCTORS_TABLE = "instructors";

	public InstructorAPI(Vertx vertx, String tenantId) {
		this.pgClient = PostgresClient.getInstance(vertx, tenantId);
		this.pgClient.setIdField("id");
	}

	@Override
	public void getInstructors(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
		logger.info("GET instructors...");
		PgQuery.PgQueryBuilder queryBuilder = new PgQuery.PgQueryBuilder(ALL_FIELDS, INSTRUCTORS_TABLE).query(query).offset(offset).limit(limit);
		Future.succeededFuture(queryBuilder)
			.compose(this::runGetQuery)
			.compose(this::parseGetResults)
			.setHandler(asyncResultHandler);
	}

	@Override
	public void postInstructors(String lang, Instructor entity, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
		logger.info("POST instructors not implemented...");
		throw new Exception("postInstructors not implemented.");
	}

	private Future<Object[]> runGetQuery(PgQuery.PgQueryBuilder queryBuilder) {
		Future<Object[]> future = Future.future();
		try {
			PgQuery query = queryBuilder.build();
			pgClient.get(query.getTable(), Instructor.class, query.getFields(), query.getCql(), true, false,
					future.completer());
		} catch (Exception e) {
			future.fail(new Throwable(e));
		}
		return future;
	}

	@SuppressWarnings("unchecked")
	public Future<Response> parseGetResults(Object[] resultSet) {
		List<Instructor> instructors = (List<Instructor>) resultSet[0];
		int totalRecords = (Integer) resultSet[1];
		InstructorCollection instructorCollection = new InstructorCollection();
		instructorCollection.setInstructors(instructors);
		instructorCollection.setTotalRecords(totalRecords);
		Response response = GetInstructorsResponse.withJsonOK(instructorCollection);
		return Future.succeededFuture(response);
	}
}
