package tsdb.raw;

import java.io.Serializable;

import tsdb.DataQuality;
import tsdb.TimeConverter;
import tsdb.util.Pair;
import tsdb.util.Util;

/**
 * entry in TimestampSeries
 * @author woellauer
 *
 */
public class TsEntry implements Serializable {
	private static final long serialVersionUID = -4568612556987905958L;
	public final long timestamp;	
	public final float[] data;
	public final DataQuality[] qualityFlag; // maybe null
	public final int[][] qualityCounter; // maybe null
	public final boolean[] interpolated; // maybe null

	public TsEntry(long timestamp, float[] data, DataQuality[] qualityFlag,int[][] qualityCounter) {
		this(timestamp, data, qualityFlag, qualityCounter, null);
	}

	public TsEntry(long timestamp, float[] data, DataQuality[] qualityFlag,int[][] qualityCounter,boolean[] interpolated) {
		this.timestamp = timestamp;
		this.data = data;
		this.qualityFlag = qualityFlag;
		this.qualityCounter = qualityCounter;
		this.interpolated = interpolated;
	}

	public TsEntry(long timestamp,float[] data) {
		this(timestamp, data, null, null, null);
	}

	public TsEntry(long timestamp, float[] data, DataQuality[] qualityFlag) {
		this(timestamp, data, qualityFlag, null, null);
	}

	public TsEntry(long timestamp, Pair<float[],DataQuality[]> dataPair) {
		this(timestamp,dataPair.a,dataPair.b);
	}

	public TsEntry(long timestamp, Object unused, Pair<float[],int[][]> dataPair) {		
		this(timestamp,dataPair.a, null,dataPair.b, null);
		if(unused!=null) {
			throw new RuntimeException();
		}
	}

	public static TsEntry createNaN(long timestamp, int columnCount) {
		return new TsEntry(timestamp, getNanData(columnCount), getNanQuality(columnCount));
	}

	public static float[] getNanData(int columnCount) {
		float[] data = new float[columnCount];
		for(int i=0;i<columnCount;i++) {
			data[i] = Float.NaN;
		}
		return data;
	}

	public static DataQuality[] getNanQuality(int columnCount) {
		DataQuality[] qualityFlag = new DataQuality[columnCount];
		for(int i=0;i<columnCount;i++) {
			qualityFlag[i] = DataQuality.Na;
		}
		return qualityFlag;
	}

	@Override
	public String toString() {
		return timestamp+"|"+TimeConverter.oleMinutesToLocalDateTime(timestamp)+" "+Util.arrayToString(data)+" "+qualityFlagToString()+" "+interpolatedFlagToString()+" "+qualityCounterToString();
	}

	public String qualityFlagToString() {
		if(qualityFlag==null) {
			return "q-";
		} else {
			String s="q";
			for(int qIndex=0; qIndex<qualityFlag.length; qIndex++) {
				switch(qualityFlag[qIndex]) {
				case Na:
					s+='n';
					break;
				case NO:
					s+='0';
					break;
				case PHYSICAL:
					s+='1';
					break;
				case STEP:
					s+='2';
					break;
				case EMPIRICAL:
					s+='3';
					break;
				default:
					s+='?';

				}
			}
			return s;
		}
	}

	private String interpolatedFlagToString() {
		if(interpolated==null) {
			return "i-";
		} else {
			String s="i";
			for(int qIndex=0; qIndex<interpolated.length; qIndex++) {
				s+=interpolated[qIndex];
			}
			return s;			
		}
	}
	
	private String qualityCounterToString() {
		if(qualityCounter==null) {
			return "c-";
		} else {
			return "c?";
		}
	}



}