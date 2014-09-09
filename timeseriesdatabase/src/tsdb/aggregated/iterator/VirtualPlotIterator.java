package tsdb.aggregated.iterator;

import java.util.ArrayList;
import java.util.List;

import tsdb.DataQuality;
import tsdb.raw.TimeSeriesEntry;
import tsdb.util.ProcessingChainEntry;
import tsdb.util.TimeSeriesSchema;
import tsdb.util.Util;
import tsdb.util.iterator.MoveIterator;
import tsdb.util.iterator.TimeSeriesIterator;

public class VirtualPlotIterator extends MoveIterator {

	public static TimeSeriesSchema createSchema(String[] result_schema, TimeSeriesIterator[] input_iterator) {
		if(input_iterator.length<1) {
			throw new RuntimeException("VirtualPlotIterator needs at least one input_iterator");
		}
		String[] schema = result_schema;
		boolean constantTimeStep = input_iterator[0].getOutputTimeSeriesSchema().constantTimeStep;
		int timeStep = input_iterator[0].getOutputTimeSeriesSchema().timeStep;
		boolean isContinuous = input_iterator[0].getOutputTimeSeriesSchema().isContinuous;		
		boolean hasQualityFlags = input_iterator[0].getOutputTimeSeriesSchema().hasQualityFlags; // TODO
		boolean hasInterpolatedFlags = false; // TODO
		boolean hasQualityCounters = false; // TODO
		for(TimeSeriesIterator it:input_iterator) {
			if(!it.getOutputTimeSeriesSchema().constantTimeStep) {
				constantTimeStep = false;
				timeStep = TimeSeriesSchema.NO_CONSTANT_TIMESTEP;
			}
			if(!it.getOutputTimeSeriesSchema().isContinuous) {
				isContinuous = false;
			}
			if(!it.getOutputTimeSeriesSchema().hasQualityFlags) {
				hasQualityFlags = false;
			}
		}
		return new TimeSeriesSchema(schema, constantTimeStep, timeStep, isContinuous, hasQualityFlags, hasInterpolatedFlags, hasQualityCounters) ;
	}

	private final String[] result_schema;
	private final TimeSeriesIterator[] processing_iterator;
	private int currentElements;
	private long currentTimestamp;
	private TimeSeriesEntry[] processing_current;
	private int[][] processing_position_index;
	
	private boolean processQualityFlags;

	public VirtualPlotIterator(String[] result_schema, TimeSeriesIterator[] input_iterator, String debugTextplotID) {
		super(createSchema(result_schema, input_iterator));
	
		this.result_schema = result_schema;
		this.processing_iterator = input_iterator;
		this.processing_current = new TimeSeriesEntry[processing_iterator.length];
		this.processing_position_index = new int[processing_iterator.length][];
		this.processQualityFlags = getOutputTimeSeriesSchema().hasQualityFlags;

		for(int iterator_index=0;iterator_index<processing_iterator.length;iterator_index++) {
			processing_position_index[iterator_index] = Util.stringArrayToPositionIndexArray(processing_iterator[iterator_index].getOutputSchema(), result_schema, true, true);
		}

		currentTimestamp=Long.MAX_VALUE;
		for(int iterator_index=0;iterator_index<processing_iterator.length;iterator_index++) {
			if(!processing_iterator[iterator_index].hasNext()) {
				throw new RuntimeException("empty input_iterator: "+iterator_index);
			}
			processing_current[iterator_index] = processing_iterator[iterator_index].next();
			if(processing_current[iterator_index].timestamp<currentTimestamp) {
				currentTimestamp = processing_current[iterator_index].timestamp; // set start timestamp
			}
		}

		//one element in every entry in current
		currentElements = processing_iterator.length;
	}

	@Override
	public List<ProcessingChainEntry> getProcessingChain() {
		List<ProcessingChainEntry> result = new ArrayList<ProcessingChainEntry>(); 
		result.add(this);
		return result;
	}

	@Override
	protected TimeSeriesEntry getNext() {
		if(currentElements==0) {
			return null;
		}

		float[] resultData = TimeSeriesEntry.getNanData(result_schema.length);
		DataQuality[] resultFlags = null;
		if(processQualityFlags) {
			resultFlags = TimeSeriesEntry.getNanQuality(result_schema.length);
		}
		for(int iterator_index=0;iterator_index<processing_iterator.length;iterator_index++) { //loop over iterators with iterator_index
			if(processing_current[iterator_index]!=null) {
				if(processing_current[iterator_index].timestamp == currentTimestamp) { // insert data into resultData
					float[] data = processing_current[iterator_index].data;
					DataQuality[] qualityFlags = processing_current[iterator_index].qualityFlag;
					final int[] x = processing_position_index[iterator_index];					

					for(int colIndex=0;colIndex<data.length;colIndex++) {
						final float value = data[colIndex];
						final int resultIndex = x[colIndex];
						if(!Float.isNaN(value)) {// ??						
							resultData[resultIndex] = value;
						}
						if(processQualityFlags) {
							resultFlags[resultIndex] = qualityFlags[colIndex];
						}
					}
					if(processing_iterator[iterator_index].hasNext()) {
						processing_current[iterator_index] = processing_iterator[iterator_index].next(); 
					} else {
						processing_current[iterator_index] = null;
						currentElements--;
					}
				}
			}
		}
		//result element
		TimeSeriesEntry resultTimeSeriesEntry = new TimeSeriesEntry(currentTimestamp, resultData, resultFlags);

		//set next element timestamp
		currentTimestamp=Long.MAX_VALUE;
		for(int iterator_index=0;iterator_index<processing_iterator.length;iterator_index++) {
			if(processing_current[iterator_index]!=null && processing_current[iterator_index].timestamp<currentTimestamp) {
				currentTimestamp = processing_current[iterator_index].timestamp; // set start timestamp
			}
		}
		return resultTimeSeriesEntry;
	}		
}

