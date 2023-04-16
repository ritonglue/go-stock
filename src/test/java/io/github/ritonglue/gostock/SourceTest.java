package io.github.ritonglue.gostock;

public class SourceTest {
	private final int id;

	public SourceTest(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj == this) return true;
		if(!(obj instanceof SourceTest)) return false;
		SourceTest a = (SourceTest) obj;
		return a.id == this.id;
	}
	
	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public String toString() {
		return String.format("SourceTest [id=%s]", id);
	}
}
