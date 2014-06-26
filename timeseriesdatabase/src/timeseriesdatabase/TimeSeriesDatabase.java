package timeseriesdatabase;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ini4j.BasicMultiMap;
import org.ini4j.Wini;
import org.ini4j.Profile.Section;

import timeseriesdatabase.aggregated.AggregationType;
import timeseriesdatabase.aggregated.TimeSeries;
import timeseriesdatabase.aggregated.GapFiller;
import timeseriesdatabase.raw.TimestampSeries;
import util.Table;
import util.Util;
import util.Util.FloatRange;
import au.com.bytecode.opencsv.CSVReader;
import de.umr.jepc.Attribute;
import de.umr.jepc.Attribute.DataType;
import de.umr.jepc.store.Event;
import de.umr.jepc.store.btree.TimeSplitBTreeEventStore;
import de.umr.jepc.util.enums.TimeRepresentation;

/**
 * This is the main class of the timeseries database.
 * @author woellauer
 *
 */
public class TimeSeriesDatabase {
	
	private static final Logger log = Util.log;
	
	/**
	 * EventStore is the storage of all time series
	 */
	public TimeSplitBTreeEventStore store;
	
	/**
	 * station/logger type name	->	LoggerType Object
	 * 00CEMU, ...
	 */
	public Map<String,LoggerType> loggerTypeMap;
	
	/**
	 * plot id	->	Station Object
	 * HEG01, ...
	 */
	public Map<String,Station> stationMap;
	
	/**
	 * general station name	->	GeneralStation Object
	 * HEG, HEW, ...
	 */
	public Map<String,GeneralStation> generalStationMap;
	
	/**
	 * sensor name	->	Sensor Object
	 * Ta_200, ...
	 */
	public Map<String,Sensor> sensorMap;
	
	/**
	 * set of sensor names of input files, that should not be stored in database
	 */
	public Set<String> ignoreSensorNameSet;
	
	/**
	 * set of sensor name, that should be included in base aggregation processing
	 */
	public Set<String> baseAggregatonSensorNameSet;
	
	/**
	 * create a new TimeSeriesDatabase object and connects to stored database files
	 * @param databasePath
	 * @param evenstoreConfigFile
	 */
	public TimeSeriesDatabase(String databasePath, String evenstoreConfigFile) {		
		log.trace("create TimeSeriesDatabase");		
		FileInputStream configStream = null; 
		try {
			configStream = new FileInputStream(evenstoreConfigFile);
		} catch (FileNotFoundException e) {
			log.error(configStream);
		}
		store = new TimeSplitBTreeEventStore(TimeRepresentation.POINT,databasePath,configStream);
		loggerTypeMap = new HashMap<String, LoggerType>();
		stationMap = new TreeMap<String,Station>();//new HashMap<String,Station>();
		generalStationMap = new HashMap<String, GeneralStation>();
		sensorMap = new TreeMap<String,Sensor>();//new HashMap<String,Sensor>();
		ignoreSensorNameSet = new HashSet<String>();
		baseAggregatonSensorNameSet = new HashSet<String>();		
		store.open();		
	}
	
	/**
	 * for each station type read schema of data, only data of names in this schema is included in the database
	 * This method creates LoggerType Objects
	 * @param configFile
	 */
	public void readLoggerSchemaConfig(String configFile) {
		try {
			Wini ini = new Wini(new File(configFile));
			for(String typeName:ini.keySet()) {
				Section section = ini.get(typeName);
				List<String> names = new ArrayList<String>();			
				for(String name:section.keySet()) {
					names.add(name);
				}
				String[] sensorNames = new String[names.size()];
				Attribute[] schema = new Attribute[names.size()+1];
				for(int i=0;i<names.size();i++) {
					String sensorName = names.get(i);
					sensorNames[i] = sensorName;
					schema[i] =  new Attribute(sensorName,DataType.FLOAT);
					if(!sensorMap.containsKey(sensorName)) {
						sensorMap.put(sensorName, new Sensor(sensorName));
					}
				}
				schema[sensorNames.length] = new Attribute("sampleRate",DataType.SHORT);
				loggerTypeMap.put(typeName, new LoggerType(typeName, sensorNames,schema));
			}
		} catch (Exception e) {
			log.error(e);
		}
	}

	/**
	 * read list of sensors that should be included in gap filling processing
	 * @param configFile
	 */
	public void readGapFillingConfig(String configFile) {
		try {
			Wini ini = new Wini(new File(configFile));
			Section section = ini.get("gap_filling");
			for(String name:section.keySet()) {
				Sensor sensor = sensorMap.get(name);
				if(sensor!=null) {
					sensor.useGapFilling = true;
				} else {
					log.warn("gap filling config: sensor not found: "+name);
				}
			}

		} catch (Exception e) {
			log.error(e);
		}
	}
	
	/**
	 * reads properties of stations and creates Station Objects
	 * @param configFile
	 */
	public void readStationConfig(String configFile) {
		Map<String,List<Map<String,String>>> plotIdMap = readStationConfigInternal(configFile);
		
		for(Entry<String, List<Map<String, String>>> entryMap:plotIdMap.entrySet()) {
			if(entryMap.getValue().size()!=1) {
				log.error("multiple properties for one station not implemented:\t"+entryMap.getValue());
			} else {
				String plotID = entryMap.getKey();
				String generalStationName = plotID.substring(0, 3);
				Station station = new Station(this, generalStationName, plotID, entryMap.getValue().get(0));
				stationMap.put(plotID, station);
			}
		}	
	}
	
	/**
	 * reads properties of stations
	 * @param configFile
	 */
	private static Map<String,List<Map<String,String>>> readStationConfigInternal(String config_file) {
		try {
			CSVReader reader = new CSVReader(new FileReader(config_file));
			List<String[]> list = reader.readAll();			
			String[] names = list.get(0);			
			final String NAN_TEXT = "NaN";			
			Map<String,Integer> nameMap = new HashMap<String,Integer>();			
			for(int i=0;i<names.length;i++) {
				if(!names[i].equals(NAN_TEXT)) {
					if(nameMap.containsKey(names[i])) {
						log.error("dublicate name: "+names[i]);
					} else {
						nameMap.put(names[i], i);
					}
				}
			}

			String[][] values = new String[list.size()-1][];
			for(int i=1;i<list.size();i++) {
				values[i-1] = list.get(i);
			}

			Map<String,List<Map<String,String>>> plotidMap = new HashMap<String,List<Map<String,String>>>();
			int plotidIndex = nameMap.get("PLOTID");
			for(String[] row:values) {
				String plotid = row[plotidIndex];
				List<Map<String,String>> entries = plotidMap.get(plotid);
				if(entries==null) {
					entries = new ArrayList<Map<String,String>>(1);
					plotidMap.put(plotid, entries);
				}				

				Map<String,String> valueMap = new HashMap<String, String>();
				for(Entry<String, Integer> mapEntry:nameMap.entrySet()) {

					String value = row[mapEntry.getValue()];
					if(!value.toUpperCase().equals(NAN_TEXT.toUpperCase())) {
						valueMap.put(mapEntry.getKey(), value);
					}					
				}

				entries.add(valueMap);
			}
			return plotidMap;			
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * registers streams for all containing stations (with stream name == plotID)
	 */
	public void registerStreams() {
		for(Station station:stationMap.values()) {
			store.registerStream(station.plotID, station.getLoggerType().schema);
		}
	}
	
	/**
	 * clears all stream data in EventStore; deletes all database files
	 */
	public void clear() {
		store.clear();
	}
	
	/**
	 * close EventStore, all pending stream data is written to disk
	 */
	public void close() {
		store.close();
	}
	
	/**
	 * loads all files of one exploratory HEG, HEW, ...
	 * directory structure example: [exploratoriyPath]/HG01/20080130_^b0_0000.dat ... 
	 * @param exploratoriyPath
	 */
	public void loadDirectoryOfOneExploratory(Path exploratoriyPath) {
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
				
				
				if(!stationMap.containsKey(stationID)) {
					log.error("station does not exist in database:\t"+stationID);
				} else {				
					Station station = stationMap.get(stationID);
					station.loadDirectoryOfOneStation(stationPath);
				}
			}
		} catch (IOException e) {
			log.error(e);
		}
		
	}
	
	/**
	 * creates a map of all entries in one section of an "ini"-file
	 * @param section
	 * @return
	 */
	private Map<String, String> readSensorNameMap(Section section) {
		Map<String,String> sensorNameMap = new HashMap<String, String>();
		for(String key:section.keySet()) {
			if(!key.equals("NaN")) {
				sensorNameMap.put(key, section.get(key));
			}
		}
		return sensorNameMap;
	}
	
	/**
	 * read config for sensors: physical minimum and maximum values
	 * @param configFile
	 */
	public void readSensorPhysicalRangeConfig(String configFile) {
		List<FloatRange> list = Util.readIniSectionFloatRange(configFile,"parameter_physical_range");
		if(list!=null) {
			for(FloatRange entry:list) {
				Sensor sensor = sensorMap.get(entry.name);
				if(sensor != null) {
					sensor.physicalMin = entry.min;
					sensor.physicalMax = entry.max;
				} else {
					log.warn("sensor not found: "+entry.name);
				}
			}
		}
	}
	
	public void readSensorEmpiricalRangeConfig(String configFile) {
		List<FloatRange> list = Util.readIniSectionFloatRange(configFile,"parameter_empirical_range");
		if(list!=null) {
			for(FloatRange entry:list) {
				Sensor sensor = sensorMap.get(entry.name);
				if(sensor != null) {
					sensor.empiricalMin = entry.min;
					sensor.empiricalMax = entry.max;
				} else {
					log.warn("sensor not found: "+entry.name);
				}
			}
		}
	}
	
	public void readSensorStepRangeConfig(String configFile) {
		List<FloatRange> list = Util.readIniSectionFloatRange(configFile,"paramter_step_range");
		if(list!=null) {
			for(FloatRange entry:list) {
				Sensor sensor = sensorMap.get(entry.name);
				if(sensor != null) {
					sensor.stepMin = entry.min;
					sensor.stepMax = entry.max;
				} else {
					log.warn("sensor not found: "+entry.name);
				}
			}
		}
	}
	
	/**
	 * reads config for translation of input sensor names to database sensor names
	 * @param configFile
	 */
	public void readSensorNameTranslationConfig(String configFile) {		
		final String SENSOR_NAME_CONVERSION_HEADER_SUFFIX = "_header_0000";		
		try {
			Wini ini = new Wini(new File(configFile));
			for(LoggerType loggerType:loggerTypeMap.values()) {
				log.trace("read config for "+loggerType.typeName);
				Section section = ini.get(loggerType.typeName+SENSOR_NAME_CONVERSION_HEADER_SUFFIX);
				if(section!=null) {
					loggerType.sensorNameTranlationMap = readSensorNameMap(section);
				} else {
					log.warn("logger type name tranlation not found:\t"+loggerType.typeName);
				}
			}
			
			final String NAME_CONVERSION_HEADER_SOIL_SUFFIX = "_soil_parameters_header_0000";
			for(Section section:ini.values()) {
				String sectionName = section.getName();
				for(GeneralStation generalStation:generalStationMap.values()) {
					String prefix = "000"+generalStation.name;
					if(sectionName.startsWith(prefix)) {
						String general_section = prefix+"xx"+NAME_CONVERSION_HEADER_SOIL_SUFFIX;
						if(sectionName.equals(general_section)) {
							generalStation.sensorNameTranlationMap = readSensorNameMap(section);
						} else if(sectionName.endsWith(NAME_CONVERSION_HEADER_SOIL_SUFFIX)) {
							String plotID = sectionName.substring(3, 8);
							Station station = stationMap.get(plotID);
							if(station!=null) {
								station.sensorNameTranlationMap = readSensorNameMap(section);
							} else {
								log.warn("station does not exist: "+plotID);
							}
						} else {
							log.warn("unknown: "+sectionName);
						}
					}				
				}
			}
		} catch (Exception e) {
			log.error(e);
		}
	}
	
	/**
	 * reads names of used general stations
	 * @param configFile
	 */
	public void readGeneralStationConfig(String configFile) {		
		try {
			Wini ini = new Wini(new File(configFile));
			Section section = ini.get("general_stations");
			for(String name:section.keySet()) {				
				generalStationMap.put(name, new GeneralStation(name));
			}

		} catch (Exception e) {
			log.error(e);
		}		
	}

	/**
	 * reads names of input sensors, that should not be included in database
	 * @param configFile
	 */
	public void readIgnoreSensorNameConfig(String configFile) {		
		try {
			Wini ini = new Wini(new File(configFile));
			Section section = ini.get("ignore_sensors");
			for(String name:section.keySet()) {				
				ignoreSensorNameSet.add(name);
			}

		} catch (Exception e) {
			log.error(e);
		}	
	}
	
	/**
	 * reads sensor config for base aggregation: for each sensor the type of aggregation is read
	 * @param configFile
	 */
	public void readBaseAggregationConfig(String configFile) {
		try {
			Wini ini = new Wini(new File(configFile));
			Section section = ini.get("base_aggregation");
			if(section!=null) {
				for(String sensorName:section.keySet()) {
					Sensor sensor =sensorMap.get(sensorName);
					if(sensor!=null) {
						String aggregateTypeText = section.get(sensorName);
						AggregationType aggregateType = AggregationType.NONE;
						if(aggregateTypeText.toLowerCase().equals("average")) {
							aggregateType = AggregationType.AVERAGE;
						} else if(aggregateTypeText.toLowerCase().equals("sum")) {
							aggregateType = AggregationType.SUM;
						} else if(aggregateTypeText.toLowerCase().equals("maximum")) {
							aggregateType = AggregationType.MAXIMUM;							
						} else if(aggregateTypeText.toLowerCase().equals("average_wind_direction")) {
							aggregateType = AggregationType.AVERAGE_WIND_DIRECTION;
						} else if(aggregateTypeText.toLowerCase().equals("average_wind_velocity")) {
							aggregateType = AggregationType.AVERAGE_WIND_VELOCITY;							
						} else if(aggregateTypeText.toLowerCase().equals("average_zero")) {
							aggregateType = AggregationType.AVERAGE_ZERO;							
						} else {
							log.warn("aggregate type unknown: "+aggregateTypeText+"\tin\t"+sensorName);
						}
						sensor.baseAggregationType = aggregateType;
						baseAggregatonSensorNameSet.add(sensorName);
					} else {
						log.warn("sensor not found: "+sensorName);
					}
				}
			}
		} catch (IOException e) {
			log.warn(e);
		}		
		
		
		
	}
	
	/**
	 * loads all files of all exploratories
	 * directory structure example: [exploratoriesPath]/HEG/HG01/20080130_^b0_0000.dat ... 
	 * @param exploratoriesPath
	 */
	public void loadDirectoryOfAllExploratories(Path exploratoriesPath) {
		log.info("load exploratories:\t"+exploratoriesPath);
		try {
			DirectoryStream<Path> stream = Files.newDirectoryStream(exploratoriesPath);
			for(Path path:stream) {
				System.out.println(path);
				loadDirectoryOfOneExploratory(path);
			}
		} catch (IOException e) {
			log.error(e);
		}
		
	}

	/**
	 * sql query on storage of raw time series
	 * (not used)
	 * @param sql
	 * @return iterator of raw sensor data
	 */
	public Iterator<Event> query(String sql) {
		return store.query(sql);		
	}
	
	/**
	 * query stream of plotID within startTime and endTime
	 * (not used)
	 * @param plotID
	 * @param ParameterName
	 * @param startTime
	 * @param endTime
	 * @return iterator over the stream of plotID with full schema
	 */
	public  Iterator<Event> queryTimeSeries(String plotID, long startTime, long endTime) {
		return store.getHistoryRange(plotID, startTime, endTime);		
	}
	
	/**
	 * Get base aggregated data
	 * @param plotID
	 * @param querySensorNames sensors in the result schema; if null all available sensors are in the result schema
	 * @return
	 */
	public TimestampSeries queryBaseAggregatedData(String plotID,String[] querySensorNames) {		
		Station station = stationMap.get(plotID);
		if(station==null) {
			log.warn("plotID not found: "+plotID);
			return TimestampSeries.EMPTY_TIMESERIES; 				
		}
		return station.queryBaseAggregatedData(querySensorNames,null,null);		
	}
	
	/**
	 * Get base aggregated data
	 * @param plotID
	 * @param querySensorNames sensors in the result schema; if null all available sensors are in the result schema
	 * @param start
	 * @param end
	 * @return
	 */
	public TimestampSeries queryBaseAggregatedData(String plotID, String[] querySensorNames, Long start, Long end) {		
		Station station = stationMap.get(plotID);
		if(station==null) {
			log.warn("plotID not found: "+plotID);
			return TimestampSeries.EMPTY_TIMESERIES; 				
		}
		TimestampSeries timeseries = station.queryBaseAggregatedData(querySensorNames, start, end);
		return timeseries;
	}
	
	/**
	 * get time series with gap filled data if possible
	 * @param plotID
	 * @param querySensorNames
	 * @param start	may be null
	 * @param end	may be null
	 * @return
	 */
	public TimeSeries queryBaseAggregatedDataGapFilled(String plotID, String [] querySensorNames, Long start, Long end) {		
		final int STATION_INTERPOLATION_COUNT = 15;		
		final int TRAINING_TIME_INTERVAL = 60*24*7*4; // in minutes;  four weeks
		//final int TIME_STEP = BaseAggregationTimeUtil.AGGREGATION_TIME_INTERVAL;
		
		Station station = stationMap.get(plotID);
		if(station==null) {
			log.warn("plotID not found: "+plotID);
			return null; 				
		}
		
		TimestampSeries timeseries;
		if(start==null) {
			timeseries = station.queryBaseAggregatedData(querySensorNames, null, end);
		} else {
			timeseries = station.queryBaseAggregatedData(querySensorNames, start-TRAINING_TIME_INTERVAL, end);
		}
		
		long startTimestamp = timeseries.getFirstTimestamp();
		long endTimestamp = timeseries.getLastTimestamp();

		TimeSeries resultBaseTimeSeries = TimeSeries.toBaseTimeSeries(startTimestamp, endTimestamp, timeseries);		

		Station[] interpolationStations = new Station[STATION_INTERPOLATION_COUNT];
		TimeSeries[] interpolationBaseTimeseries = new TimeSeries[STATION_INTERPOLATION_COUNT];
		for(int i=0;i<STATION_INTERPOLATION_COUNT;i++) {
			interpolationStations[i] = station.nearestStationList.get(i);
			TimestampSeries interpolationTimeseries = interpolationStations[i].queryBaseAggregatedData(querySensorNames, null, null);
			interpolationBaseTimeseries[i] = TimeSeries.toBaseTimeSeries(startTimestamp, endTimestamp, interpolationTimeseries);
		}		
		
		for(String parameterName:querySensorNames) {
			if(sensorMap.get(parameterName).useGapFilling) {
				GapFiller.process(interpolationBaseTimeseries, resultBaseTimeSeries, parameterName);
			}
		}		
	
		return resultBaseTimeSeries.getClipped(start, end);
	}
	
	/**
	 * get full time series of one plotid
	 * @param plotID
	 * @return
	 */
	public TimestampSeries queryRawData(String plotID) {
		Station station = stationMap.get(plotID);
		if(station==null) {
			log.warn("plotID not found: "+plotID);
			return TimestampSeries.EMPTY_TIMESERIES; 				
		}
		return station.queryRawData(null,null,null);	
	}
	
	/**
	 * get full time series of one plotid
	 * @param plotID
	 * @param querySensorNames
	 * @return
	 */
	public TimestampSeries queryRawData(String plotID, String[] querySensorNames) {
		Station station = stationMap.get(plotID);
		if(station==null) {
			log.warn("plotID not found: "+plotID);
			return TimestampSeries.EMPTY_TIMESERIES; 				
		}
		return station.queryRawData(querySensorNames,null,null);	
	}
	
	/**
	 * get time series of one plotid and time interval
	 * @param plotID
	 * @param querySensorNames
	 * @param start
	 * @param end
	 * @return
	 */
	public TimestampSeries queryRawData(String plotID, String[] querySensorNames, Long start,Long end) {
		Station station = stationMap.get(plotID);
		if(station==null) {
			log.warn("plotID not found: "+plotID);
			return TimestampSeries.EMPTY_TIMESERIES; 				
		}
		return station.queryRawData(querySensorNames,start,end);	
	}

	/**
	 * get array of Sensor objects with given sensor names
	 * @param sensorNames
	 * @return
	 */
	public Sensor[] getSensors(String[] sensorNames) {
		Sensor[] sensors = new Sensor[sensorNames.length];
		for(int i=0;i<sensorNames.length;i++) {
			Sensor sensor = sensorMap.get(sensorNames[i]);
			sensors[i] = sensor;
			if(sensor==null) {
				log.warn("sensor "+sensorNames+" not found");
			}
		}
		return sensors;
	}
	
	/**
	 * read geo config of stations:
	 * 1. read geo pos of station
	 * 2. calculate ordered list for each station of stations nearest to current station within same general station
	 * @param config_file
	 */
	public void readStationGeoPositionConfig(String config_file) {
		try{		
			Table table = Table.readCSV(config_file);		
			int plotidIndex = table.getColumnIndex("PlotID");
			int epplotidIndex = table.getColumnIndex("EP_Plotid"); 
			int lonIndex = table.getColumnIndex("Lon");
			int latIndex = table.getColumnIndex("Lat");			
			for(String[] row:table.rows) {
				String plotID = row[epplotidIndex];
				if(!plotID.endsWith("_canceled")) { // ignore plotid canceled positions
					Station station = stationMap.get(plotID);
					if(station!=null) {					
						try {					
							double lon = Double.parseDouble(row[lonIndex]);
							double lat = Double.parseDouble(row[latIndex]);					
							station.geoPoslongitude = lon;
							station.geoPosLatitude = lat;					
						} catch(Exception e) {
							log.warn("geo pos not read: "+plotID);
						}
						if(plotidIndex>-1) {
							station.serialID = row[plotidIndex];
						}
					} else {
						log.warn("station not found: "+row[epplotidIndex]+"\t"+row[lonIndex]+"\t"+row[latIndex]);
					}
				}
				
			}
			
		} catch(Exception e) {
			log.error(e);
		}		
		calcNearestStations();		
	}
	
	public void updateGeneralStations() {
		
		for(GeneralStation g:generalStationMap.values()) {
			g.stationList = new ArrayList<Station>();
		}
		
		for(Station station:stationMap.values()) {
			generalStationMap.get(station.generalStationName).stationList.add(station);
		}
		
	}
	
	public void calcNearestStations() {
		updateGeneralStations();
		
		for(Station station:stationMap.values()) {
			
			
			double[] geoPos = transformCoordinates(station.geoPoslongitude,station.geoPosLatitude);
			
			List<Object[]> differenceList = new ArrayList<Object[]>();
			
			List<Station> stationList = generalStationMap.get(station.generalStationName).stationList;
			//System.out.println(station.plotID+" --> "+stationList);
			for(Station targetStation:stationList) {
				if(station!=targetStation) { // reference compare
					double[] targetGeoPos = transformCoordinates(targetStation.geoPoslongitude,targetStation.geoPosLatitude);
					double difference = getDifference(geoPos, targetGeoPos);
					differenceList.add(new Object[]{difference,targetStation});
				}
			}
			
			differenceList.sort(new Comparator<Object[]>() {

				@Override
				public int compare(Object[] o1, Object[] o2) {
					double d1 = (double) o1[0];
					double d2 = (double) o2[0];					
					return Double.compare(d1, d2);
				}
			});
			
			List<Station> targetStationList = new ArrayList<Station>(differenceList.size());
			for(Object[] targetStation:differenceList) {
				targetStationList.add((Station) targetStation[1]);
			}
			
			station.nearestStationList = targetStationList;
			//System.out.println(station.plotID+" --> "+station.nearestStationList);
		}
		
	}
	
	public static double[] transformCoordinates(double longitude, double latitude) {
		// TODO: do real transformation
		return new double[]{longitude,latitude};
	}
	
	public static double getDifference(double[] geoPos, double[] targetGeoPos) {
		return Math.sqrt((geoPos[0]-targetGeoPos[0])*(geoPos[0]-targetGeoPos[0])+(geoPos[1]-targetGeoPos[1])*(geoPos[1]-targetGeoPos[1]));
	}

}
