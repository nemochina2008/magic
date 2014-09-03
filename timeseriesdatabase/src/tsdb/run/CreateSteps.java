package tsdb.run;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tsdb.DataQuality;
import tsdb.FactoryTsDB;
import tsdb.GeneralStation;
import tsdb.Station;
import tsdb.TsDB;
import tsdb.VirtualPlot;
import tsdb.graph.Continuous;
import tsdb.graph.ContinuousGen;
import tsdb.graph.Difference;
import tsdb.graph.Differential;
import tsdb.graph.NodeGen;
import tsdb.graph.QueryPlan;
import tsdb.raw.TimeSeriesEntry;
import tsdb.util.iterator.TimeSeriesIterator;
import tsdb.util.iterator.TimeSeriesIteratorIterator;

public class CreateSteps {
	
	private static final String CSV_OUTPUT_PATH = "C:/timeseriesdatabase_output/";
	
	public static void main(String[] args) throws FileNotFoundException {
		System.out.println("start...");
		TsDB tsdb = FactoryTsDB.createDefault();
		ContinuousGen continuousGen = QueryPlan.getContinuousGen(tsdb, DataQuality.PHYSICAL);

		for(String sensorName:tsdb.getBaseAggregationSensorNames()) {
			System.out.println("process: "+sensorName);
			String[] schema = new String[]{sensorName};
			List<TimeSeriesIterator> iterator_list = new ArrayList<TimeSeriesIterator>();
			
			List<String> stationNames = new ArrayList<String>();
			for(GeneralStation gs:tsdb.getGeneralStations()) {
				
				for(Station station:gs.stationList) {
					if(station.isValidBaseSchema(schema)){
						stationNames.add(station.stationID);
					}
				}
				
				for(VirtualPlot virtualPlot:gs.virtualPlots) {
					if(virtualPlot.isValidBaseSchema(schema)) {
						stationNames.add(virtualPlot.plotID);
					}
				}
				
			}
			
			List<String> insertedNames = new ArrayList<String>();
			
			for(String stationName:stationNames) {
				Continuous source = continuousGen.get(stationName, schema);
				//TimeSeriesIterator it = Difference.createFromGroupAverage(tsdb, source, stationName).get(null, null);
				TimeSeriesIterator it = Differential.create(tsdb, source).get(null, null);
				if(it!=null&&it.hasNext()) {
					iterator_list.add(it);
					insertedNames.add(stationName);
				}
			}
			System.out.println("included stations("+insertedNames.size()+"): "+insertedNames);
			if(!iterator_list.isEmpty()) {
				TimeSeriesIteratorIterator result_iterator = new TimeSeriesIteratorIterator(iterator_list,schema);
				//result_iterator.writeCSV(CSV_OUTPUT_PATH+"AverageDiff/"+sensorName+".csv");
				FileOutputStream out = new FileOutputStream(CSV_OUTPUT_PATH+"Steps/"+sensorName);
				PrintStream printStream = new PrintStream(out);
				
				
				ArrayList<Float> result_list = new ArrayList<Float>();
				while(result_iterator.hasNext())  {
					TimeSeriesEntry element = result_iterator.next();
					float value = element.data[0];
					if(!Float.isNaN(value)) {
						result_list.add(value);
					}
				}
				System.out.println("sort...");
				result_list.sort(null);
				System.out.println("final calc...");
				
				float prevValue = Float.NaN;
				float prevDiff = Float.NaN;
				long startIndex = result_list.size();
				startIndex*=999;
				startIndex/=1000;
				for(Float value:result_list.subList((int) startIndex, result_list.size())) {					
					float diff = value-prevValue;
					float diffdiff = diff-prevDiff;
					if(!Float.isNaN(diff)&&!Float.isNaN(diffdiff)) {
					printStream.format(Locale.ENGLISH,"%3.3f %3.5f %3.5f\n", value, diff, diffdiff);
					}					
					prevValue = value;
					prevDiff = diff;
				}
				printStream.close();
			}
		}


		System.out.println("...end");
	}

}
