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
import org.folio.rest.jaxrs.model.Queuer;
import org.folio.rest.jaxrs.model.Waitlist;
import org.folio.rest.jaxrs.model.WaitlistCollection;
import org.folio.rest.jaxrs.resource.WaitlistsResource;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.utils.NotifyEmail;
import javax.mail.Transport;
import org.folio.rest.utils.PgQuery;
import org.folio.rest.utils.PgTransaction;
import org.folio.rest.utils.TimerManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;

public class WaitlistAPI implements WaitlistsResource {

	private static final Logger logger = LoggerFactory.getLogger(WaitlistAPI.class);

	private final PostgresClient pgClient;
	private static final String[] ALL_FIELDS = { "*" };
	private static final String QUEUERS_TABLE = "queuers";
	private static final String WAITLIST_TABLE = "waitlists";

	private static final long TICK_INTERVAL = 5000;
	public static final AtomicLong periodicLock = new AtomicLong(-1L);

	public WaitlistAPI(Vertx vertx, String tenantId) {
		this.pgClient = PostgresClient.getInstance(vertx, tenantId);
		this.pgClient.setIdField("id");
		periodicLock.updateAndGet(
				value -> value < 0 ? vertx.setPeriodic(TICK_INTERVAL, id -> onInterval(id, tenantId)) : value);
	}

	@Override
	public void getWaitlists(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
		logger.info("GET waitlists...");
		PgQuery.PgQueryBuilder pgQueryBuilder = new PgQuery.PgQueryBuilder(ALL_FIELDS, WAITLIST_TABLE).query(query).offset(offset).limit(limit);
		Future.succeededFuture(pgQueryBuilder)
			.compose(this::runGetQuery)
			.compose(this::parseGetResults)
			.setHandler(asyncResultHandler);
	}

	@Override
	public void postWaitlists(String lang, Waitlist entity, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
		logger.info("POST waitlists...");
		PgTransaction<Waitlist> pgTransaction = new PgTransaction<Waitlist>(entity);
		Future.succeededFuture(pgTransaction)
			.compose(this::startTx)
			.compose(this::saveWaitlist)
			.compose(this::endTx)
			.compose(this::parsePostResults)
			.setHandler(asyncResultHandler);
	}

	@Override
	public void getWaitlistsByWaitlistId(String waitlistId, String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
		logger.info(String.format("GET waitlist %s...", waitlistId));
		Future.succeededFuture(new GetWaitlistById(waitlistId))
			.compose(this::runGetWaitlistById)
			.compose(this::parseGetWaitlistById)
			.setHandler(asyncResultHandler);
	}

	@Override
	public void putWaitlistsByWaitlistId(String waitlistId, String lang, Waitlist entity,
			Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
			throws Exception {
		logger.info(String.format("PUT waitlist %s...", waitlistId));
		Future.succeededFuture(new PutWaitlistById(waitlistId, entity))
			.compose(this::getWaitlistById)
			.compose(this::getQueuerByWaitlistId)
			.compose(this::updateByWaitlistId)
			.compose(this::parsePutResults)
			.setHandler(asyncResultHandler);
	}

	@Override
	public void deleteWaitlistsByWaitlistId(String waitlistId, String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
		logger.info(String.format("DELETE waitlist %s not implemented...", waitlistId));
		throw new Exception("deleteWaitlistsByWaitlistId not implemented.");
	}

	private void onInterval(long timerId, String tenantId) {
		Future.succeededFuture(new WaitlistTask())
			.compose(this::queryWaitlists)
			.compose(this::queryQueuers)
			.compose(this::reduceQueuers)
			.compose(this::notifyQueuers)
			.setHandler(res -> this.createWaitlistTaskHandler(res, timerId, tenantId));
	}

	/** getWaitlists */
	private Future<Object[]> runGetQuery(PgQuery.PgQueryBuilder queryBuilder) {
		Future<Object[]> future = Future.future();
		try {
			PgQuery query = queryBuilder.build();
			pgClient.get(query.getTable(), Waitlist.class, query.getFields(), query.getCql(), true, false,
					future.completer());
		} catch (Exception e) {
			future.fail(new Throwable(e));
		}
		return future;
	}

	@SuppressWarnings("unchecked")
	public Future<Response> parseGetResults(Object[] resultSet) {
		List<Waitlist> waitlists = (List<Waitlist>) resultSet[0];
		int totalRecords = (Integer) resultSet[1];
		WaitlistCollection waitlistCollection = new WaitlistCollection();
		waitlistCollection.setWaitlists(waitlists);
		waitlistCollection.setTotalRecords(totalRecords);
		Response getWaitlistsResponse = GetWaitlistsResponse.withJsonOK(waitlistCollection);
		return Future.succeededFuture(getWaitlistsResponse);
	}

	/** postWaitlists */
	private Future<PgTransaction<Waitlist>> startTx(PgTransaction<Waitlist> tx) {
		Future<PgTransaction<Waitlist>> future = Future.future();
		pgClient.startTx(sqlConnection -> {
			tx.sqlConnection = sqlConnection;
			future.complete(tx);
		});
		return future;
	}

	private Future<PgTransaction<Waitlist>> saveWaitlist(PgTransaction<Waitlist> tx) {
		Future<PgTransaction<Waitlist>> future = Future.future();
		try {
			tx.entity = TimerManager.stopTimer(tx.entity);
			tx.entity.setCreateDate(Date.from(Instant.now()));
			pgClient.save(tx.sqlConnection, WAITLIST_TABLE, tx.entity, location -> {
				tx.location = location;
				future.complete(tx);
			});
		} catch (Exception e) {
			future.fail(new Throwable(e));
		}
		return future;
	}

	private Future<PgTransaction<Waitlist>> endTx(PgTransaction<Waitlist> tx) {
		Future<PgTransaction<Waitlist>> future = Future.future();
		pgClient.endTx(tx.sqlConnection, v -> {
			future.complete(tx);
		});
		return future;
	}

	private Future<Response> parsePostResults(PgTransaction<Waitlist> tx) {
		final String location = tx.location.result();
		final Waitlist waitlist = tx.entity;
		waitlist.setId(tx.entity.getId());
		OutStream entity = new OutStream();
		entity.setData(waitlist);
		Response response = PostWaitlistsResponse.withJsonCreated(location, entity);
		return Future.succeededFuture(response);
	}

	/** getWaitlistsByWaitlistId */
	@SuppressWarnings({ "unchecked" })
	private Future<GetWaitlistById> runGetWaitlistById(GetWaitlistById query) {
		Future<GetWaitlistById> future = Future.future();
		try {
			Criterion criterion = createCriteria("'id'", "=", query.id);
			pgClient.get(WAITLIST_TABLE, Waitlist.class, criterion, true, false, getReply -> {
				List<Waitlist> waitlists = (List<Waitlist>) getReply.result()[0];
				Waitlist waitlist = null;
				if (waitlists.size() > 0) {
					waitlist = waitlists.get(0);
					if (waitlist.getTimerState().equals("started")) {
						waitlist = TimerManager.updateTimer(waitlist);
					}
				}
				query.waitlist = waitlist;
				future.complete(query);
			});
		} catch (Exception e) {
			future.fail(new Throwable(e));
		}
		return future;
	}

	public Future<Response> parseGetWaitlistById(GetWaitlistById query) {
		Response getWaitlistsByWaitlistIdResponse = GetWaitlistsByWaitlistIdResponse.withJsonOK(query.waitlist);
		return Future.succeededFuture(getWaitlistsByWaitlistIdResponse);
	}

	/** putWaitlistsByWaitlistId */
	@SuppressWarnings({ "unchecked" })
	private Future<PutWaitlistById> getWaitlistById(PutWaitlistById update) {
		Future<PutWaitlistById> future = Future.future();
		try {
			Criterion criterion = createCriteria("'id'", "=", update.waitlistId);
			pgClient.get(WAITLIST_TABLE, Waitlist.class, criterion, true, false, getReply -> {
				List<Waitlist> waitlists = (List<Waitlist>) getReply.result()[0];
				update.oldWaitlist = waitlists.size() > 0 ? waitlists.get(0) : null;
				future.complete(update);
			});
		} catch (Exception e) {
			future.fail(new Throwable(e));
		}
		return future;
	}

	@SuppressWarnings({ "unchecked" })
	private Future<PutWaitlistById> getQueuerByWaitlistId(PutWaitlistById update) {
		Future<PutWaitlistById> future = Future.future();
		try {
			Criterion criterion = createCriteria("'waitlistId'", "=", update.waitlistId);
			pgClient.get(QUEUERS_TABLE, Queuer.class, criterion, true, false, getReply -> {
				List<Queuer> queuers = (List<Queuer>) getReply.result()[0];
				for (final Queuer q1 : queuers) {
					final Queuer q2 = update.nextQueuer;
					final boolean shouldReplace = (q2 == null) || (q1.getCreateDate().before(q2.getCreateDate()));
					update.nextQueuer = shouldReplace ? q1 : q2;
				}
				future.complete(update);
			});
		} catch (Exception e) {
			future.fail(new Throwable(e));
		}
		return future;
	}

	private Future<PutWaitlistById> updateByWaitlistId(PutWaitlistById update) {
		Future<PutWaitlistById> future = Future.future();
		try {
			if (update.oldWaitlist != null) {
				final String prevState = update.oldWaitlist.getTimerState();
				final String nextState = update.newWaitlist.getTimerState();
				final String timerAction = TimerManager.getTimerAction(prevState, nextState);
				if (!timerAction.equals(TimerManager.INVALID)) {
					final Queuer queuer = update.nextQueuer;
					if (queuer != null) {
						if (timerAction.equals(TimerManager.START)) {
							if (queuer.getEmailedDate() == null) {
								final String username = queuer.getUsername();
								final String email = queuer.getEmail();
								final String title = update.oldWaitlist.getTitle();
								final NotifyEmail notifyEmail = new NotifyEmail(username, email, title);
								this.sendEmail(notifyEmail);
								queuer.setEmailedDate(Date.from(Instant.now()));
								this.update(queuer);
							}
						} else if (timerAction.equals(TimerManager.STOP)) {
							this.delete(queuer);
						}
					}
					update.oldWaitlist = TimerManager.applyTimerAction(update.oldWaitlist, timerAction);
					Criterion criterion = createCriteria("'id'", "=", update.waitlistId);
					pgClient.update(WAITLIST_TABLE, update.oldWaitlist, criterion, true, putReply -> {
						update.resMessage = putReply.failed() ? putReply.cause().getMessage() : "";
						future.complete(update);
					});
				} else {
					update.resMessage = String.format("Invalid timer state transition: %s to %s.", prevState,
							nextState);
					future.complete(update);
				}
			} else {
				update.resMessage = String.format("No entity with id '%s' was found", update.newWaitlist.getId());
				future.complete(update);
			}
		} catch (Exception e) {
			future.fail(new Throwable(e));
		}
		return future;
	}

	private Future<Response> parsePutResults(PutWaitlistById update) {
		Response response = PutWaitlistsByWaitlistIdResponse.withNoContent();
		response = update.resMessage.isEmpty() ? response
				: PutWaitlistsByWaitlistIdResponse.withPlainInternalServerError(update.resMessage);
		return Future.succeededFuture(response);
	}

	/** onInterval */	
	@SuppressWarnings({ "unchecked" })
	private Future<WaitlistTask> queryWaitlists(WaitlistTask waitlistTask) {
		Future<WaitlistTask> future = Future.future();
		try {
			PgQuery query = waitlistTask.waitlistQueryBuilder.build();
			pgClient.get(query.getTable(), Waitlist.class, query.getFields(), query.getCql(), true, false, res -> {
				waitlistTask.waitlists = res.succeeded() ? (List<Waitlist>) res.result()[0] : new ArrayList<Waitlist>();
				future.complete(waitlistTask);
			});
		} catch (Exception e) {
			future.fail(new Throwable(e));
		}
		return future;
	}

	@SuppressWarnings({ "unchecked" })
	private Future<WaitlistTask> queryQueuers(WaitlistTask waitlistTask) {
		Future<WaitlistTask> future = Future.future();
		try {
			PgQuery query = waitlistTask.queuerQueryBuilder.build();
			pgClient.get(query.getTable(), Queuer.class, query.getFields(), query.getCql(), true, false, res -> {
				waitlistTask.queuers = res.succeeded() ? (List<Queuer>) res.result()[0] : new ArrayList<Queuer>();
				future.complete(waitlistTask);
			});
		} catch (Exception e) {
			future.fail(new Throwable(e));
		}
		return future;
	}

	private Future<WaitlistTask> reduceQueuers(WaitlistTask waitlistTask) {
		List<Queuer> queuers = (List<Queuer>) waitlistTask.queuers;
		final Map<String, Tuple<Queuer, Queuer>> queuersMap = new HashMap<>();
		for (final Queuer q1 : queuers) {
			Tuple<Queuer, Queuer> tuple = queuersMap.get(q1.getWaitlistId());
			tuple = tuple == null ? new Tuple<Queuer, Queuer>() : tuple;
			if (shouldReplace(q1, tuple.first)) {
				tuple.second = tuple.first;
				tuple.first = q1;
			} else if (shouldReplace(q1, tuple.second)) {
				tuple.second = q1;
			}
			queuersMap.put(q1.getWaitlistId(), tuple);
		}
		waitlistTask.queuerMap = queuersMap;
		return Future.succeededFuture(waitlistTask);
	}

	private Future<WaitlistTask> notifyQueuers(WaitlistTask waitlistTask) {
		final Map<String, Tuple<Queuer, Queuer>> queuerMap = waitlistTask.queuerMap;
		final List<Waitlist> waitlists = waitlistTask.waitlists;
		for (final Waitlist waitlist : waitlists) {

			// unpack waitlist
			final String waitlistId = waitlist.getId();
			final String timerState = waitlist.getTimerState();
			final boolean timerIsStopped = timerState.equals("stopped");
			final boolean timerIsStarted = timerState.equals("started");

			// unpack queuer
			final Tuple<Queuer, Queuer> queuers = queuerMap.get(waitlistId);
			final boolean queuerIsWaiting = queuers != null;

			String msg = "";
			msg += String.format("Waitlist-%s\n", waitlistId);
			msg += "----------------\n";
			if (!queuerIsWaiting) {
				msg += "no one is waiting...\n";
				if (!timerIsStopped) {
					msg += "but timer is not stopped...\n";
					TimerManager.stopTimer(waitlist);
					this.update(waitlist);
					msg += "so stop timer...\n";
				}
			} else if (timerIsStarted) {
				msg += "someone is waiting and timer is started...\n";
				TimerManager.updateTimer(waitlist);
				int remainingTime = waitlist.getRemainingTime();
				boolean timerIsExpired = remainingTime <= 0;
				if (timerIsExpired) {
					msg += "but is expired...\n";
					this.delete(queuers.first);
					msg += "so deleted queuer...\n";
					if (queuers.second != null) {
						final String username = queuers.second.getUsername();
						final String email = queuers.second.getEmail();
						final String title = waitlist.getTitle();
						final NotifyEmail notifyEmail = new NotifyEmail(username, email, title);
						this.sendEmail(notifyEmail);
						queuers.second.setEmailedDate(Date.from(Instant.now()));
						this.update(queuers.second);
						TimerManager.startTimer(waitlist);
						this.update(waitlist);
						msg += "and sent the second queuer an email and restarted timer...\n";
					} else {
						TimerManager.stopTimer(waitlist);
						this.update(waitlist);
						msg += "and stopped timer...\n";
					}
				} else {
					msg += "and is not expired...\n";
					long remSecs = remainingTime / (1000);
					msg += String.format("and has %d seconds left...\n", remSecs);
				}
			} else {
				msg += "someone is waiting, but timer is not started...\n";
			}
			msg += "----------------\n";
			logger.info(msg);
		}
		return Future.succeededFuture(waitlistTask);
	}

	// helpers	
	private boolean sendEmail(NotifyEmail notifyEmail) {
		boolean succeeded = false;
		try {
			Transport.send(notifyEmail.compose());
			logger.info(String.format("Sent notification e-mail to %s.", notifyEmail.email));
			succeeded = true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return succeeded;
	}

	private Future<String> update(final Waitlist waitlist) {
		Future<String> future = Future.future();
		try {
			Criterion criterion = createCriteria("'id'", "=", waitlist.getId());
			pgClient.update(WAITLIST_TABLE, waitlist, criterion, true, putReply -> {
				String message = putReply.failed() ? putReply.cause().getMessage() : "";
				future.complete(message);
			});
		} catch (Exception e) {
			future.fail(new Throwable(e));
		}
		return future;
	}

	private Future<String> update(final Queuer queuer) {
		Future<String> future = Future.future();
		try {
			Criterion criterion = createCriteria("'id'", "=", queuer.getId());
			pgClient.update(QUEUERS_TABLE, queuer, criterion, true, putReply -> {
				String message = putReply.failed() ? putReply.cause().getMessage() : "";
				future.complete(message);
			});
		} catch (Exception e) {
			future.fail(new Throwable(e));
		}
		return future;
	}

	private Future<String> delete(final Queuer queuer) {
		Future<String> future = Future.future();
		try {
			Criterion criterion = createCriteria("'id'", "=", queuer.getId());
			pgClient.delete(QUEUERS_TABLE, criterion, deleteReply -> {
				String msg = deleteReply.failed() ? "Not Found" : "";
				future.complete(msg);
			});
		} catch (Exception e) {
			future.fail(new Throwable(e));
		}
		return future;
	}

	private void createWaitlistTaskHandler(AsyncResult<WaitlistTask> res, long id, String tenantId) {
		logger.info(String.format("Id %d: Running waitlist task for %s", id, tenantId));
		if (res.succeeded()) {
			logger.info("success");
		} else {
			logger.info("failure");
			logger.debug(res.cause().getMessage());
		}
	}

	private boolean shouldReplace(Queuer q1, Queuer q2) {
		return (q2 == null) || (q1.getCreateDate().before(q2.getCreateDate()));
	}

	private Criterion createCriteria(String field, String op, String value) {
		Criteria criteria = new Criteria();
		criteria.addField(field);
		criteria.setOperation(op);
		criteria.setValue(value);
		return new Criterion(criteria);
	}

	/** pojos */
	private class WaitlistTask {
		public PgQuery.PgQueryBuilder waitlistQueryBuilder = new PgQuery.PgQueryBuilder(ALL_FIELDS, WAITLIST_TABLE)
				.query(null).offset(0).limit(1000);
		public List<Waitlist> waitlists;
		public Map<String, Tuple<Queuer, Queuer>> queuerMap;
		public PgQuery.PgQueryBuilder queuerQueryBuilder = new PgQuery.PgQueryBuilder(ALL_FIELDS, QUEUERS_TABLE)
				.query(null).offset(0).limit(1000);
		public List<Queuer> queuers;
	}

	private class Tuple<X, Y> {
		public X first = null;
		public Y second = null;

		@Override
		public String toString() {
			String msg = "";
			msg += "{\n" + "\tfirst: " + first + ",\n" + "\tsecond: " + second + "\n" + "}\n";
			return msg;
		}
	}

	private class GetWaitlistById {
		private String id;
		private Waitlist waitlist;

		public GetWaitlistById(String id) {
			this.id = id;
		}
	}

	private class PutWaitlistById {
		private String waitlistId;
		private Waitlist newWaitlist;
		private Waitlist oldWaitlist;
		private Queuer nextQueuer;
		private String resMessage;

		public PutWaitlistById(String waitlistId, Waitlist newWaitlist) {
			this.waitlistId = waitlistId;
			this.newWaitlist = newWaitlist;
			this.oldWaitlist = null;
			this.nextQueuer = null;
			this.resMessage = null;
		}
	}
}
