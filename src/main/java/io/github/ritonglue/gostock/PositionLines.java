package io.github.ritonglue.gostock;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PositionLines implements Serializable {
	private static final long serialVersionUID = 1L;

	private final List<Position> closedPositions = new ArrayList<>();
	private final List<Position> openedPositions = new ArrayList<>();

	public List<Position> getClosedPositions() {
		return closedPositions;
	}
	public List<Position> getOpenedPositions() {
		return openedPositions;
	}

}
