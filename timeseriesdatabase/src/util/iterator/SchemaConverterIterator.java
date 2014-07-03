package util.iterator;

import timeseriesdatabase.raw.TimestampSeriesEntry;
import util.TimeSeriesSchema;
import util.Util;

public class SchemaConverterIterator extends MoveIterator {

	private TimeSeriesIterator input_iterator;
	private int[] inputPos;

	public SchemaConverterIterator(TimeSeriesIterator input_iterator, String[] outputSchema, boolean fillWithNaN) {
		super(new TimeSeriesSchema(outputSchema));
		this.input_iterator = input_iterator;
		this.inputPos = Util.stringArrayToPositionIndexArray(outputSchema, input_iterator.getOutputSchema(), !fillWithNaN);
	}

	@Override
	protected TimestampSeriesEntry getNext() {
		if(input_iterator.hasNext()) {
			TimestampSeriesEntry e = input_iterator.next();
			float[] data = new float[inputPos.length];
			for(int i=0;i<data.length;i++) {
				int pos = inputPos[i];
				if(pos==-1) {
					data[i] = Float.NaN;
				} else {
					data[i] = e.data[pos];
				}					
			}
			return new TimestampSeriesEntry(e.timestamp,data);
		} else {
			return null;
		}
	}

}