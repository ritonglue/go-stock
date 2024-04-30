package io.github.ritonglue.gostock.exception;

/**
 * Exception is raised if there is no more position
 */
@SuppressWarnings("serial")
public class EmptyPositionModificationException extends RuntimeException {

	public EmptyPositionModificationException() {
		super("Can't apply modification to an empty list");
	}
}
