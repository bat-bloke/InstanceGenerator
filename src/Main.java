import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import org.apache.commons.io.FileUtils;

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

	private static int n;

	private static double minLat;
	private static double minLon;
	private static double maxLat;
	private static double maxLon;

	private static int startTime;

	private static double droneSpd;
	private static double droneMean;
	private static double droneSDev;
	private static double circMean;
	private static double circSDev;
	private static double cVanProb;

	private static double[] trafficMean = new double[24];
	private static double[] trafficSDev = new double[24];

	public static void main(String[] args) {

		File jarPath = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath());
		filePath = jarPath.getParentFile().getAbsolutePath();
		filePath = filePath.replace("%20", " ");

		//readSettings
		readSettings();
		//readTraffic
		readTraffic();
		//read input
		readInput();
		//assign site properties - drone/cVans
		assignModality();

		//instances are all 4 hours
		//0-4 with delivery point/first site at centroid
		//5-9 with random delivery point
		//driving time is free flow plus fudge based on DfT traffic flow data (assuming free flow up to 125% of average flow)
		//cycling time is free flow
		//drone distance is based on a gaussian distribution (params on input), times are based on avg grounds speed

		boolean centroid = true;
		for (int i = 0; i < 10; i++) {

			if (i > 5) {
				centroid = false;
			}
			ArrayList<Surgery> instance = selectSites(centroid);
			String runPath = filePath + "//output//" + instance.size() + "-" + i;
			new File(runPath).mkdirs();
			//generate matrices & write
			Matrix.getMatrices(instance, droneSpd, circMean, circSDev, filePath, runPath, startTime, trafficMean,
					trafficSDev);
			//write case to file
			try {
				writeToFile(instance, runPath + "//" + instance.size() + "-" + i, centroid);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static void readTraffic() {
		try {
			BufferedReader br = new BufferedReader(new FileReader(filePath + "/input/trafficModel.txt"));
			br.readLine();
			for (int i = 0; i < 24; i++) {
				String[] lineSplit = br.readLine().split("\t");
				trafficMean[i] = Double.valueOf(lineSplit[1]);
				trafficSDev[i] = Double.valueOf(lineSplit[2]);
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void readSettings() {
		try {
			BufferedReader br = new BufferedReader(new FileReader(filePath + "/input/settings.txt"));
			minLat = Double.valueOf(br.readLine().split("=")[1]);
			minLon = Double.valueOf(br.readLine().split("=")[1]);
			maxLat = Double.valueOf(br.readLine().split("=")[1]);
			maxLon = Double.valueOf(br.readLine().split("=")[1]);
			n = Integer.valueOf(br.readLine().split("=")[1]);
			startTime = Integer.valueOf(br.readLine().split("=")[1]);
			droneSpd = Double.valueOf(br.readLine().split("=")[1]);
			droneMean = Double.valueOf(br.readLine().split("=")[1]);
			droneSDev = Double.valueOf(br.readLine().split("=")[1]);
			circMean = Double.valueOf(br.readLine().split("=")[1]);
			circSDev = Double.valueOf(br.readLine().split("=")[1]);
			cVanProb = Double.valueOf(br.readLine().split("=")[1]);
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void readInput() {

		toVisit = new ArrayList<Surgery>();
		int count = 0;
		double sumLat = 0;
		double sumLon = 0;
		String line = "";
		try {
			BufferedReader br = new BufferedReader(new FileReader(filePath + "/input/postcodes.csv"));
			br.readLine();
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
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		target = new Surgery("Hospital", "TARGET", new Point2D.Double(sumLon / count, sumLat / count));
		target.setModesPermitted(1, 1, 1, 0);
	}

	private static ArrayList<Surgery> selectSites(boolean centroid) {
		ArrayList<Surgery> shuffled = new ArrayList<Surgery>(toVisit);
		Collections.shuffle(shuffled, rand);

		ArrayList<Surgery> toUse = new ArrayList<Surgery>();
		if (centroid == true) {
			toUse.add(target);
			toUse.addAll(shuffled.subList(0, n - 1));
		} else {
			toUse.addAll(shuffled.subList(0, n));
		}
		return toUse;
	}

	private static void assignModality() {
		//set modes permitted. Vans allowed everywhere, bikes allowed but will be limited by distance? (alt: use point density?)
		//drones permitted based on probability
		//mean = 0.0823, sd = 0.0306
		double droneProb = rand.nextGaussian() * droneSDev + droneMean;
		int droneUnsuitable = (int) Math.round(toVisit.size() * (1 - droneProb));
		int cVanSuitable = (int) Math.round(toVisit.size() * cVanProb);

		ArrayList<Surgery> shuffled = new ArrayList<Surgery>(toVisit);
		Collections.shuffle(shuffled);
		for (int i = 0; i < droneUnsuitable; i++) {
			shuffled.get(i).disableDrone();
		}
		Collections.shuffle(shuffled);
		for (int i = 0; i < cVanSuitable; i++) {
			shuffled.get(i).enableCVan();
		}
	}

	private static void writeToFile(ArrayList<Surgery> chosen, String path, boolean centroid) throws IOException {

		File output = new File(path + "-summary.txt");
		FileUtils.copyFile(new File(filePath + "/input/settings.txt"), output);
		FileWriter write = new FileWriter(path + "-summary.txt", true);
		PrintWriter printLine = new PrintWriter(write);

		FileWriter write2 = new FileWriter(path + "-locationsOnly.csv", false);
		PrintWriter printLine2 = new PrintWriter(write2);

		printLine.print("\n");
		printLine.print("*");
		printLine.print("\n");

		String t = "\t";
		printLine.print("Input Size:");
		printLine.print("\n");
		printLine.print(String.valueOf(toVisit.size()));
		printLine.print("\n");
		//printLine.print("Instance Size:");
		//printLine.print("\n");
		//printLine.print(String.valueOf(n));
		//printLine.print("\n");
		printLine.print("*");
		printLine.print("\n");
		printLine.print(
				"Site/Postcode" + t + "Lat" + t + "Lon" + t + "Van" + t + "Bike" + t + "Drone" + t + "C-Van Base");
		printLine.print("\n");
		printLine.print("Mean/Target" + t + target.getCoord().getY() + t + target.getCoord().getX() + t + 1 + t + 1 + t
				+ 1 + t + 0);
		printLine.print("\n");

		printLine2.print(
				"Site/Postcode" + t + "Lat" + t + "Lon" + t + "Van" + t + "Bike" + t + "Drone" + t + "C-Van Base");
		printLine2.print("\n");

		if (centroid == true) {
			printLine2.print("Mean/Target" + t + target.getCoord().getY() + t + target.getCoord().getX() + t + 1 + t + 1
					+ t + 1 + t + 0);
			printLine2.print("\n");
		} else {
			Surgery s = chosen.get(0);
			printLine2.print(s.getPostcode() + t + s.getCoord().getY() + t + s.getCoord().getX() + t + 1 + t + 1 + t + 1
					+ t + 0);
			printLine2.print("\n");
		}

		for (int i = 1; i < chosen.size(); i++) {
			Surgery s = chosen.get(i);
			int vanFlag = s.getPermittedModes()[0];
			int bikeFlag = s.getPermittedModes()[1];
			int droneFlag = s.getPermittedModes()[2];
			int consolBaseFlag = s.getPermittedModes()[3];
			printLine.print(s.getPostcode() + t + s.getCoord().getY() + t + s.getCoord().getX() + t + vanFlag + t
					+ bikeFlag + t + droneFlag + t + consolBaseFlag);
			printLine.print("\n");
			printLine2.print(s.getPostcode() + t + s.getCoord().getY() + t + s.getCoord().getX() + t + vanFlag + t
					+ bikeFlag + t + droneFlag + t + consolBaseFlag);
			printLine2.print("\n");
		}

		printLine.close();
		printLine2.close();
	}
}
