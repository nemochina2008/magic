package tsdb.web.api;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tsdb.DataQuality;
import tsdb.Region;
import tsdb.TimeConverter;
import tsdb.TsDBFactory;
import tsdb.aggregated.AggregationInterval;
import tsdb.remote.RemoteTsDB;
import tsdb.util.ZipExport;

public class ZipExportProxy {
	
	private static final Logger log = LogManager.getLogger();

	private final RemoteTsDB tsdb;
	private final ExportModel model;

	private Thread workerThread;
	private ZipExport zipexport;

	private ArrayList<String> output_lines;

	private boolean finished = false;


	public ZipExportProxy(RemoteTsDB tsdb, ExportModel model) {
		this.tsdb = tsdb;
		this.model = model;
		this.output_lines = new ArrayList<String>();
	}

	public void startExport() {
		try {
		
		File tempFile = File.createTempFile("plots", ".zip", new File(TsDBFactory.get_CSV_output_path()));

		//OutputStream outputstream = new ByteArrayOutputStream();
		
		OutputStream outputstream = new BufferedOutputStream(new FileOutputStream(tempFile));
		
		Region region = model.region;
		String[] sensorNames = model.sensors;
		if(Arrays.stream(sensorNames).anyMatch(name->name.equals("WD")) && Arrays.stream(sensorNames).noneMatch(name->name.equals("WV"))) {
			sensorNames = Stream.concat(Arrays.stream(sensorNames), Stream.of("WV")).toArray(String[]::new);
		}
		String[] plotIDs = model.plots;
		AggregationInterval aggregationInterval = model.aggregationInterval;
		DataQuality dataQuality = model.quality;
		boolean interpolated = model.interpolate;
		boolean allinone = model.allinone;
		boolean desc_sensor = model.desc_sensor;
		boolean desc_plot = model.desc_plot;
		boolean desc_settings = model.desc_settings;
		boolean col_plotid = model.col_plotid;
		boolean col_timestamp = model.col_timestamp;
		boolean col_datetime = model.col_datetime;
		boolean write_header = model.write_header;
		Long startTimestamp;
		Long endTimestamp;
		if(model.timespan==0) {
			startTimestamp = null;
			endTimestamp = null;
		} else {
			startTimestamp = TimeConverter.DateTimeToOleMinutes(LocalDateTime.of(model.timespan, 1, 1, 0, 0));
			endTimestamp = TimeConverter.DateTimeToOleMinutes(LocalDateTime.of(model.timespan, 12, 31, 23, 0));
		}			
		zipexport = new ZipExport(tsdb, region, sensorNames, plotIDs, aggregationInterval, dataQuality, interpolated, allinone,desc_sensor,desc_plot,desc_settings,col_plotid,col_timestamp,col_datetime,write_header,startTimestamp,endTimestamp);
		zipexport.setPrintCallback(this::println);
		workerThread = new Thread(new Runnable() {					
			@Override
			public void run() {
				try {
					boolean ret = zipexport.writeToStream(outputstream);
					outputstream.close();
					finished = true;
				} catch(Exception e) {
					finished = true;	
				}
			}
		});

		workerThread.start();
		} catch (Exception e) {
			log.error(e);
		}
	}

	public void println() {
		synchronized (output_lines) {
			output_lines.add("");
		} 
	}


	public void println(String text) {
		synchronized (output_lines) {
			output_lines.add(text);
		}		
	}

	public String[] getOutputLines() {
		String[] lines;
		synchronized (output_lines) {
			lines = output_lines.toArray(new String[0]);
			output_lines.clear();
		}
		return lines;
	}

	public boolean getFinished() {
		return finished;
	}
	
	public int getProcessedPlots() {
		return zipexport.getProcessedPlots();
	}
}
