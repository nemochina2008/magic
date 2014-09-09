package tsdb.loader;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.logging.log4j.Logger;

import de.umr.jepc.store.Event;
import tsdb.Station;
import tsdb.TsDB;
import tsdb.TsDBClient;
import tsdb.catalog.SourceEntry;
import tsdb.util.Util;


/**
 * This class contains methods to read time series from input files in "BE"-Format and stores data into database.
 * @author woellauer
 *
 */
public class TimeSeriesLoaderBE extends TsDBClient {
	
	private final long minTimestamp;
	
	public TimeSeriesLoaderBE(TsDB tsdb, long minTimestamp) {
		super(tsdb);
		this.minTimestamp = minTimestamp;
	}
	
	/**
	 * specific to BE:
	 * read files with root folder
	 * @param rootPath
	 */
	public void loadDirectory_with_stations_structure_two(Path rootPath) {
		log.info("loadDirectory_with_stations_structure_two:\t"+rootPath);
		try {
			DirectoryStream<Path> stream = Files.newDirectoryStream(rootPath);
			for(Path stationPath:stream) {
				System.out.println(stationPath+"\t");
				String stationID = stationPath.getName(stationPath.getNameCount()-1).toString();				
				if(!tsdb.stationExists(stationID)) {
					log.error("station does not exist in database:\t"+stationID);
				} else {				
					Station station = tsdb.getStation(stationID);
					Path newPath = Paths.get(stationPath.toString(),"backup");
					if(Files.exists(newPath)) {
						loadDirectoryOfOneStation(station,newPath);
					}
				}
			}
		} catch (IOException e) {
			log.error(e);
		}		
	}
	
	/**
	 * loads all files of all exploratories
	 * directory structure example: [exploratoriesPath]/HEG/HG01/20080130_^b0_0000.dat ... 
	 * @param exploratoriesPath
	 */
	public void loadDirectoryOfAllExploratories_structure_one(Path exploratoriesPath) {
		log.info("loadDirectoryOfAllExploratories_structure_one:\t"+exploratoriesPath);
		try {
			DirectoryStream<Path> stream = Files.newDirectoryStream(exploratoriesPath);
			for(Path path:stream) {
				System.out.println(path);
				loadDirectoryOfOneExploratory_structure_one(path);
			}
		} catch (IOException e) {
			log.error(e);
		}
	}
	
	/**
	 * loads all files of one exploratory HEG, HEW, ...
	 * directory structure example: [exploratoriyPath]/HG01/20080130_^b0_0000.dat ... 
	 * @param exploratoriyPath
	 */
	public void loadDirectoryOfOneExploratory_structure_one(Path exploratoriyPath) {
		log.info("load exploratory:\t"+exploratoriyPath);
		try {
			DirectoryStream<Path> stream = Files.newDirectoryStream(exploratoriyPath);
			for(Path stationPath:stream) {
				String stationID = stationPath.subpath(stationPath.getNameCount()-1, stationPath.getNameCount()).toString();

				//*** workaround for directory names ***

				if(stationID.startsWith("HG")) {
					stationID = "HEG"+stationID.substring(2);
				} else if(stationID.startsWith("HW")) {
					stationID = "HEW"+stationID.substring(2);
				}

				//**********************************


				if(!tsdb.stationExists(stationID)) {
					log.error("station does not exist in database:\t"+stationID);
				} else {				
					Station station = tsdb.getStation(stationID);
					loadDirectoryOfOneStation(station,stationPath);
				}
			}
		} catch (IOException e) {
			log.error(e);
		}
	}
	
	/**
	 * Reads all UDBF-Files of one directory and inserts the data entries into database
	 * @param stationPath
	 */
	public void loadDirectoryOfOneStation(Station station, Path stationPath) {
		log.info("load station:\t"+stationPath+"\tplotID:\t"+station.stationID);
		System.out.println("load station:\t"+stationPath+"\tplotID:\t"+station.stationID);

		Map<String,List<Path>> fileNameMap = new TreeMap<String,List<Path>>(); // TreeMap: prefix needs to be ordered!

		try {
			DirectoryStream<Path> stream = Files.newDirectoryStream(stationPath, x -> x.toString().endsWith(".dat"));
			for(Path path:stream) {				
				String fileName = path.getFileName().toString();
				String prefix = fileName.substring(0,fileName.indexOf('_'));

				List<Path> list = fileNameMap.get(prefix);
				if(list==null) {
					list = new ArrayList<Path>();
					fileNameMap.put(prefix, list);
				}
				list.add(path);
			}			
		} catch (IOException e) {
			e.printStackTrace();
		}

		TreeMap<Long,Event> eventMap = new TreeMap<Long,Event>();

		for(Entry<String, List<Path>> entry:fileNameMap.entrySet()) {
			//String prefix = entry.getKey();
			List<Path> pathList = entry.getValue();	

			List<List<Event>> eventsList = new ArrayList<List<Event>>();

			for(Path path:pathList) {
				try {
					UDBFTimestampSeries timeSeries = readUDBFTimeSeries(station.stationID, path);
					List<Event> eventList = translateToEvents(station, timeSeries, minTimestamp);
					if(eventList!=null) {
						eventsList.add(eventList);
						
						tsdb.sourceCatalog.insert(new SourceEntry(path,station.stationID,timeSeries.time[0],timeSeries.time[timeSeries.time.length-1],timeSeries.time.length,timeSeries.getHeaderNames(), new String[0],(int)timeSeries.timeConverter.getTimeStep().toMinutes()));
					}
				} catch (Exception e) {
					log.error("file not read: "+path+"\t"+e);
				}
			}

			@SuppressWarnings("unchecked")
			Iterator<Event>[] iterators = new Iterator[eventsList.size()];

			for(int i=0;i<eventsList.size();i++) {
				iterators[i]=eventsList.get(i).iterator();
			}

			Event[] currentEvent = new Event[iterators.length];

			for(int i=0;i<iterators.length;i++) {
				if(iterators[i].hasNext()) {
					currentEvent[i] = iterators[i].next();
				}				
			}


			long currentTimestamp = -1;
			Event collectorEvent = null;

			while(true) {

				int minIndex=-1;
				long minTimeStamp = Long.MAX_VALUE;
				for(int i=0;i<iterators.length;i++) {
					if(currentEvent[i]!=null) {
						if(currentEvent[i].getTimestamp()<minTimeStamp) {
							minTimeStamp = currentEvent[i].getTimestamp();
							minIndex = i;
						}
					}
				}

				if(minIndex<0) {
					break;
				}

				if(currentTimestamp<currentEvent[minIndex].getTimestamp()) {
					if(collectorEvent!=null) {
						if(eventMap.containsKey(collectorEvent.getTimestamp())) {
							//log.warn("event already inserted");
						} else {
							eventMap.put(collectorEvent.getTimestamp(), collectorEvent);
						}
					}
					currentTimestamp = currentEvent[minIndex].getTimestamp();
					collectorEvent = null;
				}
				if(collectorEvent==null) {
					collectorEvent = currentEvent[minIndex];
				} else {
					Object[] payload = currentEvent[minIndex].getPayload();
					Object[] collectorPayload = collectorEvent.getPayload();
					for(int i=0;i<collectorPayload.length-1;i++) { // TODO
						if(!Float.isNaN((float) payload[i])&&Float.isNaN((float) collectorPayload[i])) {
							collectorPayload[i] = payload[i];
						}
					}
				}

				if(iterators[minIndex].hasNext()) {
					currentEvent[minIndex] = iterators[minIndex].next();
				} else {
					currentEvent[minIndex] = null;
				}

			}

			if(collectorEvent!=null) {
				if(eventMap.containsKey(collectorEvent.getTimestamp())) {
					//log.warn("event already inserted");
				} else {
					eventMap.put(collectorEvent.getTimestamp(), collectorEvent);
				}
			}			
		}	

		if(eventMap.size()>0) {
			tsdb.streamStorage.insertData(station.stationID, eventMap);			
		} else {
			log.warn("no data to insert: "+stationPath);
		}
	}
	
	/**
	 * Reads an UDBF-File and return structured data as UDBFTimeSeries Object.
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	public static UDBFTimestampSeries readUDBFTimeSeries(String stationID, Path filename) throws IOException {
		log.trace("load UDBF file:\t"+filename+"\tplotID:\t"+stationID);
		UniversalDataBinFile udbFile = new UniversalDataBinFile(filename);
		UDBFTimestampSeries udbfTimeSeries = udbFile.getUDBFTimeSeries();
		udbFile.close();
		return udbfTimeSeries;
	}
	
	/**
	 * Convertes rows of input file data into events with matching schema of the event stream of this plotID 
	 * @param udbfTimeSeries
	 * @param minTimestamp minimal timestamp that should be included in result
	 * @return List of Events, time stamp ordered 
	 */
	public List<Event> translateToEvents(Station station, UDBFTimestampSeries udbfTimeSeries, long minTimestamp) {
		List<Event> resultList = new ArrayList<Event>(); // result list of events	

		//mapping: UDBFTimeSeries column index position -> Event column index position;    eventPos[i] == -1 -> no mapping		
		int[] eventPos = new int[udbfTimeSeries.sensorHeaders.length];  

		//sensor names contained in event stream schema
		String[] sensorNames = station.loggerType.sensorNames;


		//creates mapping eventPos   (  udbf pos -> event pos )
		for(int sensorIndex=0; sensorIndex<udbfTimeSeries.sensorHeaders.length; sensorIndex++) {
			eventPos[sensorIndex] = -1;
			SensorHeader sensorHeader = udbfTimeSeries.sensorHeaders[sensorIndex];
			String rawSensorName = sensorHeader.name;
			if(!tsdb.containsIgnoreSensorName(rawSensorName)) {
				String sensorName = station.translateInputSensorName(rawSensorName,true);
				//System.out.println(sensorHeader.name+"->"+sensorName);
				if(sensorName != null) {
					for(int schemaIndex=0;schemaIndex<sensorNames.length;schemaIndex++) {
						String schemaSensorName = sensorNames[schemaIndex];
						if(schemaSensorName.equals(sensorName)) {
							eventPos[sensorIndex] = schemaIndex;
						}
					}
				}
				if(eventPos[sensorIndex] == -1) {
					if(sensorName==null) {
						log.warn("sensor name not in translation map: "+rawSensorName+" -> "+sensorName+"\t"+station.stationID+"\t"+udbfTimeSeries.filename+"\t"+station.loggerType);
					} else {
						log.trace("sensor name not in schema: "+rawSensorName+" -> "+sensorName+"\t"+station.stationID+"\t"+udbfTimeSeries.filename+"\t"+station.loggerType);
					}
				}
			}
		}

		//mapping event index position -> sensor index position 
		int[] sensorPos = new int[sensorNames.length];
		for(int i=0;i<sensorPos.length;i++) {
			sensorPos[i] = -1;
		}
		int validSensorCount = 0;
		for(int i=0;i<eventPos.length;i++) {
			if(eventPos[i]>-1) {
				validSensorCount++;
				sensorPos[eventPos[i]] = i;
			}
		}

		if(validSensorCount<1) {
			log.trace("no fitting sensors in "+udbfTimeSeries.filename);
			return null; //all event columns are empty
		}

		//create events
		Object[] payload = new Object[station.loggerType.schema.length];
		short sampleRate = (short) udbfTimeSeries.timeConverter.getTimeStep().toMinutes();
		//iterate over input rows
		for(int rowIndex=0;rowIndex<udbfTimeSeries.time.length;rowIndex++) {			
			long timestamp = udbfTimeSeries.time[rowIndex];
			if(timestamp<minTimestamp) {
				continue;
			}
			
			// one input row
			float[] row = udbfTimeSeries.data[rowIndex];

			//fill event columns with input data values
			for(int attrNr=0;attrNr<sensorNames.length;attrNr++) {
				if(sensorPos[attrNr]<0) { // no input column
					payload[attrNr] = Float.NaN;
				} else {
					float value = row[sensorPos[attrNr]];				
					payload[attrNr] = value;
				}
			}

			//just for testing purpose
			if(udbfTimeSeries.time[rowIndex]==58508670) {
				System.out.println("write time 58508670 in "+station.stationID+"\t"+udbfTimeSeries.filename);
			}
				resultList.add(new Event(Arrays.copyOf(payload, payload.length), timestamp));		
		}

		return resultList;
	}	

}