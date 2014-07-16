package util.iterator;

import java.util.List;

import timeseriesdatabase.raw.TimeSeriesEntry;
import util.ProcessingChainEntry;
import util.TimeSeriesSchema;

public abstract class TimeSeriesIterator extends SchemaIterator<TimeSeriesEntry> {

	public TimeSeriesIterator(TimeSeriesSchema outputTimeSeriesSchema) {
		super(outputTimeSeriesSchema);
	}

	public abstract String getIteratorName();

	
	@Override
	public String toString() {
		
		List<ProcessingChainEntry> list = getProcessingChain();
		boolean first=true;
		String chain="";
		for(ProcessingChainEntry entry:list) {
			if(first) {				
				first=false;
			} else {
				chain+=" -> ";
			}
			chain += entry.getProcessingTitle();
		}
		
		return getIteratorName()+" "+outputTimeSeriesSchema.toString()+" "+chain;
	}
	
	@Override
	public String getProcessingTitle() {
		return getIteratorName();
	}
}