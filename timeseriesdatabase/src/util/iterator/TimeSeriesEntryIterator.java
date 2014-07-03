package util.iterator;

import java.util.Iterator;

import timeseriesdatabase.raw.TimestampSeriesEntry;
import util.TimeSeriesSchema;

public class TimeSeriesEntryIterator extends TimeSeriesIterator {
	
	private Iterator<TimestampSeriesEntry> input_iterator;

	public TimeSeriesEntryIterator(Iterator<TimestampSeriesEntry> input_iterator, String[] schema) {
		super(new TimeSeriesSchema(schema));
		this.input_iterator = input_iterator;
	}

	@Override
	public boolean hasNext() {
		return input_iterator.hasNext();
	}

	@Override
	public TimestampSeriesEntry next() {
		return input_iterator.next();
	}

}
