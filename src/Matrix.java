import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

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

	public static void getMatrices(ArrayList<Surgery> sites, double droneSpeed, double circMean, double circSDev,
			String filePath, String runID, int startHour, double[] trafficMean, double[] trafficSDev) {

		Random rand = new Random();
		//path = filePath;
		String mapSource = filePath + "//routing//map.osm.pbf";
		String carLocation = filePath + "//routing//car";
		String bikeLocation = filePath + "//routing//racingbike";

		GraphHopper graphHopperBike = new GraphHopper().setGraphHopperLocation(bikeLocation).setOSMFile(mapSource)
				.setProfiles(new Profile("vehicle").setVehicle("racingbike").setWeighting("fastest").setTurnCosts(true))
				.setMinNetworkSize(200).importOrLoad();
		GraphHopper graphHopperCar = new GraphHopper().setGraphHopperLocation(carLocation).setOSMFile(mapSource)
				.setProfiles(new Profile("vehicle").setVehicle("car").setWeighting("fastest").setTurnCosts(true))
				.setMinNetworkSize(200).importOrLoad();

		for (int hr = startHour; hr < startHour + 4; hr++) {

			//https://www.gov.uk/government/statistical-data-sets/road-traffic-statistics-tra#annual-daily-traffic-flow-and-distribution-tra03
			//tra0307
			//xx.get(orig).get(dest)
			ArrayList<ArrayList<Double>> vanTimes = new ArrayList<ArrayList<Double>>();
			ArrayList<ArrayList<Double>> vanDists = new ArrayList<ArrayList<Double>>();
			ArrayList<ArrayList<Double>> bikeTimes = new ArrayList<ArrayList<Double>>();
			ArrayList<ArrayList<Double>> bikeDists = new ArrayList<ArrayList<Double>>();
			ArrayList<ArrayList<Double>> droneTimes = new ArrayList<ArrayList<Double>>();
			ArrayList<ArrayList<Double>> droneDists = new ArrayList<ArrayList<Double>>();

			for (Surgery orig : sites) {
				int[] permitOrig = orig.getPermittedModes();
				ArrayList<Double> vt = new ArrayList<Double>();
				ArrayList<Double> vd = new ArrayList<Double>();
				ArrayList<Double> bt = new ArrayList<Double>();
				ArrayList<Double> bd = new ArrayList<Double>();
				ArrayList<Double> dt = new ArrayList<Double>();
				ArrayList<Double> dd = new ArrayList<Double>();
				if (!vanRPs.containsKey(orig)) {
					vanRPs.put(orig, new HashMap<Surgery, ResponsePath>());
				}
				if (!bikeRPs.containsKey(orig)) {
					bikeRPs.put(orig, new HashMap<Surgery, ResponsePath>());
				}
				for (Surgery dest : sites) {
					int[] permitDest = dest.getPermittedModes();
					if (orig.equals(dest)) {
						vt.add(0.0);
						vd.add(0.0);
						bt.add(0.0);
						bd.add(0.0);
						dt.add(0.0);
						dd.add(0.0);
					} else {
						ResponsePath van = null;
						HashMap<Surgery, ResponsePath> rpVan = vanRPs.get(orig);
						if (rpVan.containsKey(dest)) {
							van = rpVan.get(dest);
						} else {
							van = getGH(orig, dest, graphHopperCar, "car");
							rpVan.put(dest, van);
						}

						ResponsePath bike = null;
						HashMap<Surgery, ResponsePath> rpBike = bikeRPs.get(orig);
						if (rpBike.containsKey(dest)) {
							bike = rpBike.get(dest);
						} else {
							bike = getGH(orig, dest, graphHopperBike, "racingbike");
							rpBike.put(dest, van);
						}

						double vanDist = -1;
						double vanTime = -1;
						double bikeDist = -1;
						double bikeTime = -1;
						double droneDist = -1;
						double droneTime = -1;
						if ((permitOrig[0] == 1 || orig.equals(sites.get(0)))
								&& (permitDest[0] == 1 || dest.equals(sites.get(0)))) {
							vanDist = van.getDistance() / 1000.0;//in km
							vanTime = van.getTime() / 60000.0;//in mins
							//penalty varied with time of day, and by site
							double trafficPenaltyFactor = rand.nextGaussian() * trafficSDev[hr] + trafficMean[hr];
							vanTime = vanTime + vanTime * trafficPenaltyFactor;
						}
						if ((permitOrig[1] == 1 || orig.equals(sites.get(0)))
								&& (permitDest[1] == 1 || dest.equals(sites.get(0)))) {
							bikeDist = bike.getDistance() / 1000.0;//in km
							bikeTime = bike.getTime() / 60000.0;//in mins
						}
						if ((permitOrig[2] == 1 || orig.equals(sites.get(0)))
								&& (permitDest[2] == 1 || dest.equals(sites.get(0)))) {
							droneDist = geoDesDistance(orig, dest);//varies with time of day (assumed random)
							double droneCirc = rand.nextGaussian() * circSDev + circMean;
							droneDist = droneDist * droneCirc;
							droneTime = droneDist / (droneSpeed / 60);
						}
						vt.add(vanTime);
						vd.add(vanDist);
						bt.add(bikeTime);
						bd.add(bikeDist);
						dt.add(droneTime);
						dd.add(droneDist);
					}
				}
				vanTimes.add(vt);
				vanDists.add(vd);
				bikeTimes.add(bt);
				bikeDists.add(bd);
				droneTimes.add(dt);
				droneDists.add(dd);
			}

			String zero = "";
			if (hr < 10) {
				zero = "0";
			}

			try {
				writeMatrixToFile(vanTimes, sites, runID + "//" + zero + hr + "00hr" + "-vanTimes");
				writeMatrixToFile(vanDists, sites, runID + "//" + zero + hr + "00hr" + "-vanDists");
				writeMatrixToFile(bikeTimes, sites, runID + "//" + zero + hr + "00hr" + "-bikeTimes");
				writeMatrixToFile(bikeDists, sites, runID + "//" + zero + hr + "00hr" + "-bikeDists");
				writeMatrixToFile(droneTimes, sites, runID + "//" + zero + hr + "00hr" + "-droneTimes");
				writeMatrixToFile(droneDists, sites, runID + "//" + zero + hr + "00hr" + "-droneDists");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private static ResponsePath getGH(Surgery a, Surgery b, GraphHopper gh, String profile) {

		GHRequest request = new GHRequest(a.getCoord().getY(), a.getCoord().getX(), b.getCoord().getY(),
				b.getCoord().getX()).setProfile("vehicle");
		GHResponse route = gh.route(request);
		return route.getBest();
	}

	//writing of matrix files. Input of nested arraylist of doubles. Writes to tab separated text file
	private static void writeMatrixToFile(ArrayList<ArrayList<Double>> matrix, ArrayList<Surgery> sites, String path)
			throws IOException {

		FileWriter write = new FileWriter(path + ".txt", false);
		PrintWriter printLine = new PrintWriter(write);
		printLine.print("\t");
		for (Surgery s : sites) {
			printLine.print(s.getCoord().getY() + "," + s.getCoord().getX());
			printLine.print("\t");
		}
		printLine.print("\n");

		int i = 0;
		for (ArrayList<Double> row : matrix) {
			printLine.print(sites.get(i).getCoord().getY() + "," + sites.get(i).getCoord().getX());
			printLine.print("\t");
			for (Double weight : row) {
				printLine.print(weight);
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
		//distance in km
		Double distance = (g.s12 / 1000);
		return distance;
	}
}
