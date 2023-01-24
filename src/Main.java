import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/*
 * Generator of test instances.
 * Takes an input csv of postcode, lat, lon - POTENTIALLY CHANGE TO WHOLE COUNTRY and use bounding box
 * Randomly selects from given list - 
 * Uses graphhopper to identify distances/free-flow times for bikes and vans
 * Uses normal distribution of circuity with an avg speed for drones 
 * Site suitability - cyclable range limit from ANY 'surgery'
 * Drone sites identification - random distribution?!?!?
 * Consol van sites - random distribution???
 * Destination hospital chosen at average of input postcodes
 * LIMITATIONS - can't use Stuart area/costs
 */

// https://www.doogal.co.uk/UKPostcodes?Search=SO

public class Main {

	public static ArrayList<Surgery> toVisit = new ArrayList<Surgery>();
	public static Surgery target;

	private static Random rand = new Random();

	private static String filePath;

	private static int n = 50;

	private static double minLat = 50.7903972073004;
	private static double minLon = -1.6307092241717385;
	private static double maxLat = 50.978705845297554;
	private static double maxLon = -1.200492307493704;

	public static void main(String[] args) {

		File jarPath = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath());
		filePath = jarPath.getParentFile().getAbsolutePath();
		filePath = filePath.replace("%20", " ");

		//readSettings();

		//read input
		readInput();
		//assign site properties
		assignNotDroneable();
		//select n sites
		ArrayList<Surgery> instance = selectSites();

		//generate matrices & write
		Matrix.getMatrices(instance, 100, filePath, filePath + "//output//" + instance.size());

		//write case to file

		//return list of surgeries

	}

	private static void readInput() {

		toVisit = new ArrayList<Surgery>();
		int count = 0;
		double sumLat = 0;
		double sumLon = 0;
		String line = "";
		try {
			BufferedReader br = new BufferedReader(new FileReader(filePath + "/input/postcodes.csv"));
			System.out.println(br.readLine());
			while ((line = br.readLine()) != null) {
				String[] lineSplit = line.split(",");
				if (!lineSplit[1].equals("No")) {
					double lat = Double.parseDouble(lineSplit[2]);
					double lon = Double.parseDouble(lineSplit[3]);
					if (lat < maxLat && lat > minLat && lon < maxLon && lon > minLon) {
						Surgery s = new Surgery("Surgery" + count, lineSplit[0], new Point2D.Double(lon, lat));
						s.setModesPermitted(1, 1, 1, 0);
						//avg site bits
						sumLat += lat;
						sumLon += lon;
						toVisit.add(s);
						count++;
					}
				}
			}
			br.close();

		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		target = new Surgery("Hospital", "TARGET", new Point2D.Double(sumLon / count, sumLat / count));
		target.setModesPermitted(1, 1, 1, 0);
	}

	private static ArrayList<Surgery> selectSites() {
		ArrayList<Surgery> shuffled = new ArrayList<Surgery>(toVisit);
		Collections.shuffle(shuffled, rand);

		ArrayList<Surgery> toUse = new ArrayList<Surgery>();
		toUse.add(target);
		toUse.addAll(shuffled.subList(0, n));
		return toUse;
	}

	private static void assignNotDroneable() {
		//set modes permitted. Vans allowed everywhere, bikes allowed but will be limited by distance? (alt: use point density?)
		//drones permitted based on probability
		//mean = 0.0823, sd = 0.0306
		double droneProb = rand.nextGaussian() * 0.0306 + 0.0823;
		int droneUnsuitable = (int) Math.round(toVisit.size() * (1 - droneProb));

		ArrayList<Surgery> shuffled = new ArrayList<Surgery>(toVisit);
		Collections.shuffle(shuffled);
		for (int i = 0; i < droneUnsuitable; i++) {
			shuffled.get(i).disableDrone();
		}
	}
	/*
	private void writeToFile(ArrayList<Surgery> chosen) {
	
		String t = "\t";
		Mode van = InputExcelReader.MODES.getVan();
		Mode bike = InputExcelReader.MODES.getBike();
		Mode drone = InputExcelReader.MODES.getMode("drone");
		ArrayList<Surgery> cVanBasedAt = null;//TODO CHANGE TO LIST OF POTENTIAL BASED AT SITES
	
		String line1 = "Input Size:";
		String line2 = String.valueOf(toVisit.size());
		String line3 = "Instance Size:";
		String line4 = String.valueOf(n);
	
		String line6 = "Site/Postcode"+t+"Lat"+t+"Lon"+t+"Van"+t+"Bike"+t+"Drone"+t+"C-Van Base";
		String line7 = "Mean/Target"+t+target.getCoord().getY()+t+target.getCoord().getX()+t+1+t+1+t+1+t+0;
		for(Surgery s: chosen) {
			int vanFlag = s.getPermittedModes()[0];
			int bikeFlag = s.getPermittedModes()[1];
			int droneFlag = s.getPermittedModes()[2];
			int consolBaseFlag = s.getPermittedModes()[3];
			String line = s.getPostcode()+t+s.getCoord().getY()+t+s.getCoord().getX()+t+vanFlag+t+bikeFlag+t+droneFlag+t+consolBaseFlag;
		}
	
		String lineX = "Time Matrix"+TIME?;
		PRINT MATRICES;
		(DIST AND TIME)
	}
	
		private void assignConsolVanBasedAt() {
	
		}
	
		HashMap<LocalDateTime, HashMap<Pair<Surgery, Surgery>, HashMap<String, Double>>> timeODMatrix
	
		//take the imports from the eDrone solvers
		public synchronized void setODParams(Surgery orig, Surgery dest, int m, HashMap<String, Object> params) {
	
			Mode mode = InputExcelReader.MODES.getMode(m);
			if (!RouteDistance.externalPair.containsKey(mode.getGHProfile())) {
				RouteDistance.externalPair.put(mode.getGHProfile(), new ArrayList<Pair<Surgery, Surgery>>());
			}
	
			Pair<Surgery, Surgery> od = new Pair<Surgery, Surgery>(orig, dest);
			if (params != null) {
				od.assignExtParams(params);
				RouteDistance.externalPair.get(mode.getGHProfile()).add(od);
			} else {
				//update existing OD pair with trajectory data
				Pair<Surgery, Surgery> odCurrent = RouteDistance.externalPair.get(mode.getGHProfile())
						.get(RouteDistance.externalPair.get(mode.getGHProfile()).indexOf(od));
				odCurrent.assignTraj(traj);
	
			}
		}
	
	
	 */
}
