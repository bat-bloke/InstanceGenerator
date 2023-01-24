import java.awt.geom.Point2D;

public class Surgery {

	private String siteName;
	private String postcode;
	private Point2D.Double coordinates = null;
	private int[] modesPermitted = new int[4];
	private double droneDistToTarget;

	@Override
	public String toString() {
		return this.postcode;
	}

	public Surgery(String name, String pc, Point2D.Double coord) {

		this.siteName = name;
		this.postcode = pc;
		assignPosition(coord);
	}

	// Assigns co-ordinate to the surgery
	public void assignPosition(Point2D.Double coord) {
		this.coordinates = coord;
		double[] coords = { coord.getX(), coord.getY() };
	}

	// Getter for site name
	public String getName() {
		return this.siteName;
	}

	// Getter for postcode
	public String getPostcode() {
		return this.postcode;
	}

	// Getter for coordinates
	public Point2D.Double getCoord() {
		return this.coordinates;
	}

	public void setModesPermitted(int van, int bike, int drone, int cVanBase) {
		this.modesPermitted[0] = van;
		this.modesPermitted[1] = bike;
		this.modesPermitted[2] = drone;
		this.modesPermitted[3] = cVanBase;
	}

	public int[] getPermittedModes() {
		return this.modesPermitted;
	}

	@Deprecated
	// set distance to target hospital
	public void setDroneDist(double dist) {
		this.droneDistToTarget = dist;
	}

	@Deprecated
	// get distance to target hospital
	public double getDroneDist() {
		return this.droneDistToTarget;
	}

	public void disableBikeService() {
		this.modesPermitted[1] = 0;
	}

	public void disableDrone() {
		this.modesPermitted[2] = 0;
	}
}
