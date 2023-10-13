import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import org.apache.commons.math.util.MathUtils;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import net.sf.geographiclib.GeodesicMask;

/*
 * Class used for calculating the distance between points
 */

public class Matrix {

	private static HashMap<Surgery, HashMap<Surgery, ResponsePath>> vanRPs = new HashMap<Surgery, HashMap<Surgery, ResponsePath>>();
	private static HashMap<Surgery, HashMap<Surgery, ResponsePath>> bikeRPs = new HashMap<Surgery, HashMap<Surgery, ResponsePath>>();
	private static GraphHopper graphHopperBike;
	private static GraphHopper graphHopperCar;
	private static boolean store = false;

	public static void setUpGH(String filePath) {
		String mapSource = filePath + "//routing//map.osm.pbf";
		String carLocation = filePath + "//routing//car";
		String bikeLocation = filePath + "//routing//racingbike";

		graphHopperBike = new GraphHopper().setGraphHopperLocation(bikeLocation).setOSMFile(mapSource)
				.setProfiles(new Profile("vehicle").setVehicle("racingbike").setWeighting("fastest").setTurnCosts(true))
				.setMinNetworkSize(200).importOrLoad();
		graphHopperCar = new GraphHopper().setGraphHopperLocation(carLocation).setOSMFile(mapSource)
				.setProfiles(new Profile("vehicle").setVehicle("car").setWeighting("fastest").setTurnCosts(true))
				.setMinNetworkSize(200).importOrLoad();
	}

	public static void getMatrices(ArrayList<Surgery> sites, double droneSpeed, double circMean, double circSDev,
			String runID, int startHour, double[] trafficMean, double[] trafficSDev) {

		Random rand = new Random();
		// path = filePath;

		// https://www.gov.uk/government/statistical-data-sets/road-traffic-statistics-tra#annual-daily-traffic-flow-and-distribution-tra03
		// tra0307
		createMatrixFiles(sites, runID, startHour);
		for (int o = 0; o < sites.size(); o++) {
			Surgery orig = sites.get(o);
			int[] permitOrig = orig.getPermittedModes();
			// [van time, dist, bike time, dist, drone time, dist][hour][dest]
			double[][][] values = new double[6][4][sites.size()];
			for (int hr = startHour; hr < startHour + 4; hr++) {
				Arrays.fill(values[0][hr - startHour], -1.0);
				Arrays.fill(values[1][hr - startHour], -1.0);
				Arrays.fill(values[2][hr - startHour], -1.0);
				Arrays.fill(values[3][hr - startHour], -1.0);
				Arrays.fill(values[4][hr - startHour], -1.0);
				Arrays.fill(values[5][hr - startHour], -1.0);
			}
			for (int d = 0; d < sites.size(); d++) {
				Surgery dest = sites.get(d);
				int[] permitDest = dest.getPermittedModes();
				ResponsePath van = getGH(orig, dest, graphHopperCar, "car");
				ResponsePath bike = getGH(orig, dest, graphHopperBike, "racingbike");

				for (int hr = startHour; hr < startHour + 4; hr++) {
					if (orig.equals(dest)) {
						values[0][hr - startHour][d] = 0.0;
						values[1][hr - startHour][d] = 0.0;
						values[2][hr - startHour][d] = 0.0;
						values[3][hr - startHour][d] = 0.0;
						values[4][hr - startHour][d] = 0.0;
						values[5][hr - startHour][d] = 0.0;
					} else {
						if ((permitOrig[0] == 1 || orig.equals(sites.get(0)))
								&& (permitDest[0] == 1 || dest.equals(sites.get(0)))) {
							values[1][hr - startHour][d] = MathUtils.round(van.getDistance() / 1000.0, 3);// in km
							double time = van.getTime() / 60000.0;// in mins
							// penalty varied with time of day, and by site
							double trafficPenaltyFactor = rand.nextGaussian() * trafficSDev[hr] + trafficMean[hr];
							values[0][hr - startHour][d] = MathUtils.round(time + time * trafficPenaltyFactor, 3);
						}
						if ((permitOrig[1] == 1 || orig.equals(sites.get(0)))
								&& (permitDest[1] == 1 || dest.equals(sites.get(0)))) {
							values[3][hr - startHour][d] = MathUtils.round(bike.getDistance() / 1000.0, 3);// in km
							values[2][hr - startHour][d] = MathUtils.round(bike.getTime() / 60000.0, 3);// in mins
						}
						if ((permitOrig[2] == 1 || orig.equals(sites.get(0)))
								&& (permitDest[2] == 1 || dest.equals(sites.get(0)))) {
							double geoDist = geoDesDistance(orig, dest);// varies with time of day (assumed random)
							double droneCirc = rand.nextGaussian() * circSDev + circMean;
							double actualDist = geoDist * droneCirc;
							values[5][hr - startHour][d] = MathUtils.round(actualDist, 3);
							values[4][hr - startHour][d] = MathUtils.round(actualDist / (droneSpeed / 60), 3);
						}
					}
				}
			}
			writeToMatrixFile(values, orig, startHour, runID);
			System.out.println("All times/distances for site " + (o + 1) + "/" + sites.size() + " written.");
		}
	}

	private static void writeToMatrixFile(double[][][] values, Surgery origin, int startHour, String runID) {

		for (int hr = startHour; hr < startHour + 4; hr++) {
			String zero = "";
			if (hr < 10) {
				zero = "0";
			}
			String[] paths = new String[6];
			paths[0] = runID + "//" + zero + hr + "00hr" + "-vanTimes";
			paths[1] = runID + "//" + zero + hr + "00hr" + "-vanDists";
			paths[2] = runID + "//" + zero + hr + "00hr" + "-bikeTimes";
			paths[3] = runID + "//" + zero + hr + "00hr" + "-bikeDists";
			paths[4] = runID + "//" + zero + hr + "00hr" + "-droneTimes";
			paths[5] = runID + "//" + zero + hr + "00hr" + "-droneDists";

			for (int i = 0; i < paths.length; i++) {
				try {
					FileWriter write = new FileWriter(paths[i] + ".txt", true);
					PrintWriter printLine = new PrintWriter(write);
					printLine.print("\n");
					printLine.print(origin.getPostcode());
					double[] data = values[i][hr - startHour];
					for (int d = 0; d < data.length; d++) {
						printLine.print("\t");
						printLine.print(data[d]);
					}
					printLine.close();
				} catch (IOException e) {
					System.out.println(e);
				}
			}
		}
	}

	private static void createMatrixFiles(ArrayList<Surgery> sites, String runID, int startHour) {

		for (int hr = startHour; hr < startHour + 4; hr++) {
			String zero = "";
			if (hr < 10) {
				zero = "0";
			}
			String[] paths = new String[6];
			paths[0] = runID + "//" + zero + hr + "00hr" + "-vanTimes";
			paths[1] = runID + "//" + zero + hr + "00hr" + "-vanDists";
			paths[2] = runID + "//" + zero + hr + "00hr" + "-bikeTimes";
			paths[3] = runID + "//" + zero + hr + "00hr" + "-bikeDists";
			paths[4] = runID + "//" + zero + hr + "00hr" + "-droneTimes";
			paths[5] = runID + "//" + zero + hr + "00hr" + "-droneDists";
			for (int i = 0; i < paths.length; i++) {
				try {
					FileWriter write = new FileWriter(paths[i] + ".txt", false);
					PrintWriter printLine = new PrintWriter(write);
					printLine.print("\t");
					for (Surgery s : sites) {
						printLine.print(s.getPostcode());
						printLine.print("\t");
					}
					printLine.close();
				} catch (IOException e) {
					System.out.println(e);
				}
			}
		}
	}

	private static ResponsePath getGH(Surgery a, Surgery b, GraphHopper gh, String profile)
			throws java.lang.OutOfMemoryError {

		GHRequest request = new GHRequest(a.getCoord().getY(), a.getCoord().getX(), b.getCoord().getY(),
				b.getCoord().getX()).setProfile("vehicle");
		GHResponse route = gh.route(request);
		return route.getBest();
	}

	// writing of matrix files. Input of nested arraylist of doubles. Writes to tab
	// separated text file
	private static void writeMatrixToFile(double[][] matrix, ArrayList<Surgery> sites, String path) throws IOException {

		FileWriter write = new FileWriter(path + ".txt", false);
		PrintWriter printLine = new PrintWriter(write);
		printLine.print("\t");
		for (Surgery s : sites) {
			printLine.print(s.getCoord().getY() + "," + s.getCoord().getX());
			printLine.print("\t");
		}
		printLine.print("\n");

		int i = 0;
		for (int a = 0; a < sites.size(); a++) {
			printLine.print(sites.get(i).getCoord().getY() + "," + sites.get(i).getCoord().getX());
			printLine.print("\t");
			for (int b = 0; b < sites.size(); b++) {
				printLine.print(matrix[a][b]);
				printLine.print("\t");
			}
			printLine.print("\n");
			i++;
		}
		printLine.close();
	}

	public static Double geoDesDistance(Surgery a, Surgery b) {

		GeodesicData g = Geodesic.WGS84.Inverse(a.getCoord().getY(), a.getCoord().getX(), b.getCoord().getY(),
				b.getCoord().getX(), GeodesicMask.DISTANCE);
		// distance in km
		Double distance = (g.s12 / 1000);
		return distance;
	}
}
