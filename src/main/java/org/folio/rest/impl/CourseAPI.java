package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.Course;
import org.folio.rest.jaxrs.model.CourseCollection;
import org.folio.rest.jaxrs.resource.CoursesResource;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.utils.PgQuery;

public class CourseAPI implements CoursesResource {

	private static final Logger logger = LoggerFactory.getLogger(CourseAPI.class);

	private final PostgresClient pgClient;
	private static final String[] ALL_FIELDS = { "*" };
	private static final String COURSES_TABLE = "courses";

	public CourseAPI(Vertx vertx, String tenantId) {
		this.pgClient = PostgresClient.getInstance(vertx, tenantId);
		this.pgClient.setIdField("id");
	}

	@Override
	public void getCourses(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
		logger.info("GET courses...");
		PgQuery.PgQueryBuilder queryBuilder = new PgQuery.PgQueryBuilder(ALL_FIELDS, COURSES_TABLE).query(query).offset(offset).limit(limit);
		Future.succeededFuture(queryBuilder)
			.compose(this::runGetQuery)
			.compose(this::parseGetResults)
			.setHandler(asyncResultHandler);
	}

	@Override
	public void postCourses(String lang, Course entity, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
		logger.info("POST courses not implemented...");
		throw new Exception("postCourses not implemented.");
	}

	private Future<Object[]> runGetQuery(PgQuery.PgQueryBuilder queryBuilder) {
		Future<Object[]> future = Future.future();
		try {
			PgQuery query = queryBuilder.build();
			pgClient.get(query.getTable(), Course.class, query.getFields(), query.getCql(), true, false,
					future.completer());
		} catch (Exception e) {
			future.fail(new Throwable(e));
		}
		return future;
	}

	@SuppressWarnings("unchecked")
	public Future<Response> parseGetResults(Object[] resultSet) {
		List<Course> courses = (List<Course>) resultSet[0];
		int totalRecords = (Integer) resultSet[1];
		CourseCollection courseCollection = new CourseCollection();
		courseCollection.setCourses(courses);
		courseCollection.setTotalRecords(totalRecords);
		Response response = GetCoursesResponse.withJsonOK(courseCollection);
		return Future.succeededFuture(response);
	}
}
