package gov.usgs.traveltime;

import java.io.IOException;
import java.util.TreeMap;

import gov.usgs.traveltime.tables.MakeTables;
import gov.usgs.traveltime.tables.TablesUtil;

/**
 * Manage travel-time calculations locally, but in a manner similar to 
 * the travel time server pool.
 * 
 * @author Ray Buland
 *
 */
public class TTSessionLocal{
	String lastModel = "";
	TreeMap<String, AllBrnRef> modelData;
	MakeTables make;
	TtStatus status;
	AuxTtRef auxTT;
	AllBrnVol allBrn;
	// Set up serialization.
	String serName;											// Serialized file name for this model
	String[] fileNames;									// Raw input file names for this model

	/**
	 * Initialize auxiliary data common to all models.
	 * 
	 * @param readStats If true, read the phase statistics
	 * @param readEllip If true, read the ellipticity corrections
	 * @param readTopo If true, read the topography file
	 * @throws IOException If the auxiliary data reads fail
	 */
	public TTSessionLocal(boolean readStats, boolean readEllip, boolean readTopo) 
			throws IOException {
		// Read in data common to all models.
		try {
			auxTT = new AuxTtRef(readStats, readEllip, readTopo);
		} catch (IOException | ClassNotFoundException e1) {
			System.out.println("Unable to read auxiliary data.");
			e1.printStackTrace();
			System.exit(201);
		}
	}
	
	/**
	 * Set up a "simple" travel-time session.
	 * 
	 * @param earthModel Earth model name
	 * @param sourceDepth Source depth in kilometers
	 * @param phases Array of phase use commands
	 * @param returnAllPhases If true, provide all phases
	 * @param returnBackBranches If true, return all back branches
	 * @param tectonic If true, map Pb and Sb onto Pg and Sg
	 * @param useRSTT If true, use RSTT crustal phases
	 * @throws Exception If the depth is out of range
	 */
	public void newSession(String earthModel, double sourceDepth, 
			String[] phases, boolean returnAllPhases, boolean returnBackBranches, 
			boolean tectonic, boolean useRSTT) throws Exception {
		
		setModel(earthModel);
		allBrn.newSession(sourceDepth, phases, !returnAllPhases, !returnBackBranches, 
				tectonic, useRSTT);
	}
	
	/**
	 * Set up a "complex" travel-time session.
	 * 
	 * @param earthModel Earth model name
	 * @param sourceDepth Source depth in kilometers
	 * @param phases Array of phase use commands
	 * @param srcLat Source geographical latitude in degrees
	 * @param srcLong Source longitude in degrees
	 * @param returnAllPhases If true, provide all phases
	 * @param returnBackBranches If true, return all back branches
	 * @param tectonic If true, map Pb and Sb onto Pg and Sg
	 * @param useRSTT If true, use RSTT crustal phases
	 * @throws Exception If the depth is out of range
	 */
	public void newSession(String earthModel, double sourceDepth, 
			String[] phases, double srcLat, double srcLong, boolean returnAllPhases, 
			boolean returnBackBranches, boolean tectonic, boolean useRSTT) 
					throws Exception {
		
		setModel(earthModel);
		allBrn.newSession(srcLat, srcLong, sourceDepth, phases, !returnAllPhases, 
				!returnBackBranches, tectonic, useRSTT);
	}
	
	/**
	 * Get travel times for a "simple" session.
	 * 
	 * @param recElev Station elevation in kilometers
	 * @param delta Source receiver distance desired in degrees
	 * @return An array list of travel times
	 */
	public TTime getTT(double recElev, double delta) {
		return allBrn.getTT(recElev, delta);
	}
	
	/**
	 * Get travel times for a "complex" session.
	 * 
	 * @param recLat Receiver geographic latitude in degrees
	 * @param recLong Receiver longitude in degrees
	 * @param recElev Station elevation in kilometers
	 * @param delta Source receiver distance desired in degrees
	 * @param azimuth Receiver azimuth at the source in degrees
	 * @return An array list of travel times
	 */
	public TTime getTT(double recLat, double recLong, double recElev, 
			double delta, double azimuth) {
		return allBrn.getTT(recLat, recLong, recElev, delta, azimuth);
	}
	
	/**
	 * Get plot data suitable for a travel-time chart.
	 * 
	 * @param earthModel Earth model name
	 * @param sourceDepth Source depth in kilometers
	 * @param phases Array of phase use commands
	 * @param returnAllPhases If true, provide all phases
	 * @param returnBackBranches If true, return all back branches
	 * @param tectonic If true, map Pb and Sb onto Pg and Sg
	 * @return Travel-time plot data
	 * @throws Exception If the depth is out of range
	 */
	public TtPlot getPlot(String earthModel, double sourceDepth, String[] phases, 
			boolean returnAllPhases, boolean returnBackBranches, boolean tectonic) 
					throws Exception {
		PlotData plotData;
		
		setModel(earthModel);
		plotData = new PlotData(allBrn);
		plotData.makePlot(sourceDepth, phases, !returnAllPhases, !returnBackBranches, 
				tectonic);
		return plotData.getPlot();
	}
	
	/**
	 * Set up for a new Earth model.
	 * 
	 * @param earthModel Earth model name
	 */
	private void setModel(String earthModel) {
		AllBrnRef allRef;
		ReadTau readTau = null;

		if(!earthModel.equals(lastModel)) {
			lastModel = earthModel;
			// Initialize model storage if necessary.
			if(modelData == null) {
				modelData = new TreeMap<String,AllBrnRef>();
			}
			
			// See if we know this model.
			allRef = modelData.get(earthModel);
			
			// If not, set it up.
			if(allRef == null) {
				if(modelChanged(earthModel)) {
					if(TauUtil.useFortranFiles) {
						// Read the tables from the Fortran files.
						try {
							readTau = new ReadTau(earthModel);
							readTau.readHeader(fileNames[0]);
							readTau.readTable(fileNames[1]);
						} catch(IOException e) {
							System.out.println("Unable to read Earth model "+earthModel+".");
							System.exit(202);
						}
						// Reorganize the reference data.
						try {
							allRef = new AllBrnRef(serName, readTau, auxTT);
						} catch (IOException e) {
							System.out.println("Unable to write Earth model "+earthModel+
									" serialization file.");
						}
					} else {
						// Generate the tables.
						TablesUtil.deBugLevel = 1;
						make = new MakeTables(earthModel);
						try {
							status = make.buildModel(fileNames[0], fileNames[1]);
						} catch (Exception e) {
							System.out.println("Unable to generate Earth model "+earthModel+
									" ("+status+").");
							e.printStackTrace();
							System.exit(202);
						}
						// Build the branch reference classes.
						try {
							allRef = make.fillAllBrnRef(serName, auxTT);
						} catch (IOException e) {
							System.out.println("Unable to write Earth model "+earthModel+
									" serialization file.");
						}
					}
				} else {
					// If the model input hasn't changed, just serialize the model in.
					try {
						allRef = new AllBrnRef(serName, earthModel, auxTT);
					} catch (ClassNotFoundException | IOException e) {
						System.out.println("Unable to read Earth model "+earthModel+
								" serialization file.");
						System.exit(202);
					}
				}
//			allRef.dumpHead();
//			allRef.dumpMod('P', true);
//			allRef.dumpMod('S', true);
//			allRef.dumpBrn(true);
//			allRef.dumpBrn("pS", true);
//			allRef.dumpUp('P');
//			allRef.dumpUp('S');
				modelData.put(earthModel, allRef);
			}
			
			// Set up the (depth dependent) volatile part.
			allBrn = new AllBrnVol(allRef);
//		allBrn.dumpHead();
		}
	}
	
	/**
	 * Determine if the input files have changed.
	 * 
	 * @param earthModel Earth model name
	 * @return True if the input files have changed
	 */
	private boolean modelChanged(String earthModel) {
		// We need two files in either case.
		fileNames = new String[2];
		if(TauUtil.useFortranFiles) {
			// Names for the Fortran files.
			serName = TauUtil.model(earthModel+"_for.ser");
			fileNames[0] = TauUtil.model(earthModel+".hed");
			fileNames[1] = TauUtil.model(earthModel+".tbl");
		} else {
			// Names for generating the model.
			serName = TauUtil.model(earthModel+"_gen.ser");
			fileNames[0] = TauUtil.model("m"+earthModel+".mod");
			fileNames[1] = TauUtil.model("phases.txt");
		}
		return FileChanged.isChanged(serName, fileNames);
	}
	
	/**
	 * Get a list of available Earth models.
	 * 
	 * @return A list of available Earth model names
	 */
	public String[] getAvailModels() {
		return TauUtil.availableModels();
	}
	
	/**
	 * Get a pointer to the auxiliary travel-time information.
	 * 
	 * @return Auxiliary travel-time data
	 */
	public AuxTtRef getAuxTT() {
		return auxTT;
	}
	
	/**
	 * Print phase groups.
	 */
	public void printGroups() {
		auxTT.printGroups();
	}
	
	/**
	 * Print phase statistics.
	 */
	public void printStats() {
		auxTT.printStats();
	}
	
	/**
	 * Print phase flags.
	 */
	public void printFlags() {
		auxTT.printFlags();
	}
	
	/**
	 * Print phase table.
	 * 
	 * @param useful If true, only print "useful" phases.
	 */
	public void printTable(boolean useful) {
		allBrn.dumpTable(useful);
	}
	
	/**
	 * Print volatile phase branch information.
	 * 
	 * @param full If true, print the detailed branch specification as well
	 * @param all If true print even more specifications
	 * @param sci if true, print in scientific notation
	 * @param useful If true, only print "useful" crustal phases
	 */
	public void printBranches(boolean full, boolean all, boolean sci, 
			boolean useful) {
		allBrn.dumpBrn(full, all, sci, useful);
	}
	
	/**
	 * Print volatile phase branches that have at least one caustic.
	 * 
	 * @param full If true, print the detailed branch specification as well
	 * @param all If true print even more specifications
	 * @param sci if true, print in scientific notation
	 * @param useful If true, only print "useful" crustal phases
	 */
	public void printCaustics(boolean full, boolean all, boolean sci, 
			boolean useful) {
		allBrn.dumpCaustics(full, all, sci, useful);
	}
	
	/**
	 * Print reference phase branch information.
	 * 
	 * @param full If true, print the detailed branch specification as well
	 */
	public void printRefBranches(boolean full) {
		allBrn.ref.dumpBrn(full);
	}
}
