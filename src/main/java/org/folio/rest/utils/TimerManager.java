package org.folio.rest.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.folio.rest.jaxrs.model.Waitlist;

/**
 * Used to manage state transitions of waitlist's timer. The actions corresponding to the detected 
 * state transitions,
 * 
 * PAUSE: 
 * 	STARTED -> PAUSED
 * UNPAUSE: 
 * 	PAUSED -> STARTED
 * TURN_OFF: 
 * 	STARTED -> STOPPED
 * 	PAUSED -> STOPPED
 * TURN_ON: 	
 * 	STOPPED -> STARTED
 * INVALID:  
 * 	STOPPED -> PAUSED
 * 	Misc.s
 */
public class TimerManager {

	/** timer setting */
	private static final long NOTIFY_EXPIRE_TIME = 2 * 60 * 1000;

	private static final String STOPPED = "stopped";
	private static final String STARTED = "started";
	private static final String PAUSED = "paused";

	public static final String PAUSE = "pause";
	public static final String UNPAUSE = "unpause";
	public static final String STOP = "stop";
	public static final String START = "start";
	public static final String INVALID = "invalid";

	public static String getTimerAction(final String prev, final String next) {
		String action = INVALID;
		action = (prev.equals(STOPPED) && next.equals(STARTED)) ? START : action;
		action = (prev.equals(STARTED) && next.equals(PAUSED)) ? PAUSE : action;
		action = (prev.equals(PAUSED) && next.equals(STARTED)) ? UNPAUSE : action;
		action = (prev.equals(STARTED) && next.equals(STOPPED)) ||
				 (prev.equals(PAUSED) && next.equals(STOPPED)) ? STOP : action;
		return action;
	}

	public static Waitlist applyTimerAction(final Waitlist waitlist, final String timerAction) {
		switch (timerAction) {
			case START:
				return startTimer(waitlist);
			case STOP:
				return stopTimer(waitlist);
			case PAUSE:
				return pauseTimer(waitlist);
			case UNPAUSE:
				return unpauseTimer(waitlist);
			default:
				return null;
		}
   }

   public static Waitlist startTimer(final Waitlist waitlist) {
	   final int remainingTime = (int) NOTIFY_EXPIRE_TIME;
	   waitlist.setRemainingTime(remainingTime);
	   waitlist.setStartDate(Date.from(Instant.now()));
	   waitlist.setTimerState(STARTED);
	   return waitlist;
	}

	public static Waitlist pauseTimer(final Waitlist waitlist) {
		final int remainingTime = (int) calcRemainingTime(waitlist.getStartDate(), waitlist.getRemainingTime());	
		waitlist.setRemainingTime(remainingTime);
		waitlist.setStartDate(null);
		waitlist.setTimerState(PAUSED);
		return waitlist;
	}

	public static Waitlist unpauseTimer(final Waitlist waitlist) {
		final Instant now = Instant.now();
		waitlist.setStartDate(Date.from(now));
		waitlist.setTimerState(STARTED);
		return waitlist;
	}

	public static Waitlist stopTimer(final Waitlist waitlist) {
		waitlist.setRemainingTime(0);
		waitlist.setStartDate(null);
		waitlist.setTimerState(STOPPED);
		return waitlist;
	}

	public static Waitlist updateTimer(final Waitlist waitlist) {
		final int remainingTime = (int) calcRemainingTime(waitlist.getStartDate(), waitlist.getRemainingTime());	
		waitlist.setRemainingTime(remainingTime);
		return waitlist;
	}

	private static long calcRemainingTime(final Date startDate, long prevRemaining) {
		final Instant start = Instant.ofEpochMilli(startDate.getTime());
		final Instant now = Instant.now();
		long elapsed = Duration.between(start, now).toMillis();
		long nextRemaining = prevRemaining - elapsed;
        nextRemaining = nextRemaining < 0 ? 0 : nextRemaining;
        nextRemaining = nextRemaining > prevRemaining ? prevRemaining : nextRemaining;
		return nextRemaining;
	}
}
