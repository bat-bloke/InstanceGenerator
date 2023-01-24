import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Random;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.EncodingManager;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import net.sf.geographiclib.GeodesicMask;

/*
 * Class used for calculating the distance between points
 */

public class Matrix {

	private static String mapSource;

	private static String carLocation;
	private static String bikeLocation;

	public static void getMatrices(ArrayList<Surgery> sites, double droneSpeed, String filePath, String runID) {

		Random rand = new Random();
		//path = filePath;
		mapSource = filePath + "//routing//map.osm.pbf";
		carLocation = filePath + "//routing//car";
		bikeLocation = filePath + "//routing//racingbike";

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

			for (Surgery dest : sites) {
				int[] permitDest = dest.getPermittedModes();
				PathWrapper bike = getGH(orig, dest, bikeLocation, "bike", "");
				PathWrapper van = getGH(orig, dest, carLocation, "car", "|block_barriers=false");
				double vanDist = -1;
				double vanTime = -1;
				double bikeDist = -1;
				double bikeTime = -1;
				double droneDist = -1;
				double droneTime = -1;
				if (permitOrig[0] == 1 && permitDest[0] == 1) {
					vanDist = van.getDistance() / 1000;//in km
					vanTime = van.getTime() / 60000;//in mins
					//double trafficFudge = 
					//vanTime = vanTime + trafficFudge;
				}
				if (permitOrig[1] == 1 && permitDest[1] == 1) {
					bikeDist = bike.getDistance() / 1000;//in km
					bikeTime = bike.getTime() / 60000;//in mins
				}
				if (permitOrig[2] == 1 && permitDest[2] == 1) {
					droneDist = geoDesDistance(orig, dest);
					double droneCirc = rand.nextGaussian() * 0.1 + 1.566;
					droneDist = droneDist * droneCirc;
					droneTime = droneDist / droneSpeed;
				}
				vt.add(vanTime);
				vd.add(vanDist);
				bt.add(bikeTime);
				bd.add(bikeDist);
				dt.add(droneTime);
				dd.add(droneDist);
			}
			vanTimes.add(vt);
			vanDists.add(vd);
			bikeTimes.add(bt);
			bikeDists.add(bd);
			droneTimes.add(dt);
			droneDists.add(dd);
		}

		try {
			writeMatrixToFile(vanTimes, sites, runID + "-vanTimes");
			writeMatrixToFile(vanDists, sites, runID + "-vanDists");
			writeMatrixToFile(bikeTimes, sites, runID + "-bikeTimes");
			writeMatrixToFile(bikeDists, sites, runID + "-bikeDists");
			writeMatrixToFile(droneTimes, sites, runID + "-droneTimes");
			writeMatrixToFile(droneDists, sites, runID + "-droneDists");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/*
	 * returns bike distance between locations
	 */
	private static PathWrapper getGH(Surgery a, Surgery b, String fileLoc, String profile, String flags) {

		GraphHopper graphHopper = new GraphHopperOSM().setGraphHopperLocation(fileLoc).setDataReaderFile(mapSource)
				.setEncodingManager(EncodingManager.create(profile + "|turn_costs=true" + flags)).forServer();

		graphHopper.importOrLoad();

		GHRequest request = new GHRequest(a.getCoord().getY(), a.getCoord().getX(), b.getCoord().getY(),
				b.getCoord().getX());
		//request.putHint("calcPoints", false);
		//request.putHint("instructions", false);
		request.setWeighting("fastest");
		request.setVehicle(fileLoc);
		GHResponse route = graphHopper.route(request);

		return route.getBest();
		/*double distanceMetres = path.getDistance();
		
		//distance in km
		double distance = distanceMetres / 1000;
		
		return distance;
		
		*
		*PathWrapper path = route.getBest();
		double timeMillis = path.getTime();
		
		//time in mins
		double time = timeMillis / 60000;
		
		return time;*/
	}

	/*
	 * returns drive distance between locations
	 */

	//writing of matrix files. Input of nested arraylist of doubles. Writes to tab separated text file
	private static void writeMatrixToFile(ArrayList<ArrayList<Double>> matrix, ArrayList<Surgery> sites, String path)
			throws IOException {

		FileWriter write = new FileWriter(path + ".csv", false);
		PrintWriter printLine = new PrintWriter(write);

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
